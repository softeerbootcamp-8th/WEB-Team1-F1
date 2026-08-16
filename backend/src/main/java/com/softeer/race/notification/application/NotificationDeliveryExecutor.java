package com.softeer.race.notification.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 커밋 뒤 SSE 전송을 요청·스케줄러 스레드에서 격리하는 회원별 순차 실행기.
 *
 * <p><b>전체 흐름.</b> 요청 스레드는 {@link #execute(long, Runnable)}에서 전송 작업을 회원 큐에 넣고
 * 즉시 돌아간다. 실제 {@code SseEmitter.send()}는 {@code notification-delivery-*} 작업 스레드가
 * 나중에 실행한다. 따라서 이 클래스가 {@code @Async} 애너테이션 없이 비동기로 동작하는 이유는
 * {@link ThreadPoolExecutor#execute(Runnable)}에 작업을 직접 제출하기 때문이다.</p>
 *
 * <p>회원마다 논리 큐를 하나씩 두고, 공유 스레드 풀이 서로 다른 회원을 병렬 처리한다. 같은 회원의
 * 토스트 순서는 지키면서도 해시가 우연히 같은 정상 회원을 느린 회원 뒤에 세우지 않는다.</p>
 *
 * <p><b>일반 {@code @Async}를 쓰지 않은 이유.</b> 여러 작업 스레드에 바로 맡기면 같은 회원에게
 * 차례로 생긴 새 알림과 안 읽은 건수 변경이 반대 순서로 끝날 수 있다. 반대로 실행기 전체를 스레드
 * 하나로 만들면 느린 회원 한 명이 모든 회원을 막는다. 여기서는 {@code userId}별로만 직렬화하고
 * 서로 다른 회원은 병렬 처리해야 하므로 키를 직접 받는 실행기를 사용한다.</p>
 *
 * <p>회원별 큐와 전체 큐를 모두 제한한다. 포화 시 호출자 스레드에서 실행하지 않고 SSE 작업만
 * 버린다. 업무와 DB 알림은 이미 커밋됐고 다음 알림 목록 조회가 진실을 주므로, 실시간 전송을
 * 지키려고 업무 지연을 되살리지 않는다.</p>
 */
@Slf4j
@Component
public class NotificationDeliveryExecutor implements DisposableBean {

    static final String ACTIVE = "notification.delivery.executor.active";
    static final String QUEUED = "notification.delivery.executor.queued";
    static final String QUEUE_CAPACITY = "notification.delivery.executor.queue.capacity";
    static final String QUEUE_MAX = "notification.delivery.executor.queue.max";
    static final String TASKS = "notification.delivery.executor.tasks";

    // 실제 SSE 전송을 실행하는 플랫폼 스레드 풀. 여기의 큐에는 알림 한 건이 아니라 회원 큐를
    // 비우는 drain 작업이 들어간다. 알림 작업 전체 개수는 아래 capacity가 따로 제한한다.
    private final ThreadPoolExecutor executor;

    // 키는 회원 ID, 값은 그 회원에게 발생한 전송 작업의 FIFO 큐다.
    // ConcurrentHashMap을 쓰는 이유는 요청·경매 스케줄러 등 여러 스레드가 동시에 제출하기 때문이다.
    private final Map<Long, UserQueue> queues = new ConcurrentHashMap<>();

    // 전체 대기·실행 작업의 허용권이다. execute 때 하나를 얻고 작업이 끝나거나 폐기될 때 돌려준다.
    // 허용권이 없으면 1,024개 경계를 넘었다는 뜻이므로 새 SSE만 버린다.
    private final Semaphore capacity;

    // 전체 여유가 남아 있어도 한 회원이 이 개수 이상을 차지하지 못하게 하는 두 번째 경계다.
    private final int perUserQueueCapacity;

    // 애플리케이션 종료 때 이미 받은 작업을 기다려 주는 최대 시간이다.
    private final Duration shutdownAwait;

    // 종료가 시작된 뒤 새 작업이 들어오는 경쟁을 막는다.
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final AtomicInteger queueMaximum = new AtomicInteger();
    private final AtomicLong rejectionCount = new AtomicLong();
    private final Counter submitted;
    private final Counter completed;
    private final Counter rejected;

    public NotificationDeliveryExecutor(
            NotificationDeliveryProperties properties,
            MeterRegistry meterRegistry) {
        // @Component가 붙은 이 클래스를 스프링이 빈으로 만들면서 설정 빈과 MeterRegistry 빈을
        // 생성자에 주입한다. NotificationPusher에는 완성된 이 빈이 다시 주입된다.
        this.capacity = new Semaphore(properties.queueCapacity());
        this.perUserQueueCapacity = properties.perUserQueueCapacity();
        this.shutdownAwait = properties.shutdownAwait();
        this.executor = createExecutor(properties);
        this.submitted = taskCounter(meterRegistry, "submitted");
        this.completed = taskCounter(meterRegistry, "completed");
        this.rejected = taskCounter(meterRegistry, "rejected");

        Gauge.builder(ACTIVE, executor, ThreadPoolExecutor::getActiveCount)
                .description("알림 SSE 전용 실행기의 활성 스레드 수")
                .register(meterRegistry);
        Gauge.builder(QUEUED, queuedTasks, AtomicInteger::get)
                .description("알림 SSE 전용 실행기의 현재 대기 작업 수")
                .register(meterRegistry);
        Gauge.builder(QUEUE_CAPACITY, properties, NotificationDeliveryProperties::queueCapacity)
                .description("알림 SSE 전용 실행기의 전체 작업 용량")
                .register(meterRegistry);
        Gauge.builder(QUEUE_MAX, queueMaximum, AtomicInteger::get)
                .description("기동 뒤 관측한 알림 SSE 대기 작업 최대 수")
                .register(meterRegistry);
    }

    /**
     * 같은 회원의 작업은 같은 논리 큐에 순서대로 넣는다.
     *
     * @return 큐가 받아들였으면 {@code true}, 포화 또는 종료 중이라 SSE 작업을 버렸으면 {@code false}
     */
    public boolean execute(long userId, Runnable task) {
        submitted.increment();

        // tryAcquire는 자리가 날 때까지 기다리지 않는다. 기다리면 포화 순간에 커밋한 요청 스레드가
        // 다시 묶여 비동기 격리 목적이 사라지므로 즉시 거절한다.
        if (!accepting.get() || !capacity.tryAcquire()) {
            recordRejection(1);
            return false;
        }

        // compute 안팎에서 판단한 결과를 전달하기 위한 작은 상자다. compute는 같은 userId에 대한
        // 생성·추가·스케줄 결정을 원자적으로 묶어 회원 큐가 둘 생기는 경쟁을 막는다.
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicReference<UserQueue> shouldSchedule = new AtomicReference<>();

        queues.compute(userId, (id, current) -> {
            UserQueue queue = current != null ? current : new UserQueue();

            synchronized (queue) {
                if (queue.tasks.size() >= perUserQueueCapacity) {
                    return queue;
                }

                queue.tasks.addLast(task);
                accepted.set(true);
                int queued = queuedTasks.incrementAndGet();
                queueMaximum.accumulateAndGet(queued, Math::max);

                if (!queue.running) {
                    // running=true는 "이 회원 큐를 비우는 drain이 이미 예약됐거나 실행 중"이라는 뜻이다.
                    // 첫 작업만 drain을 예약하고, 뒤의 작업은 같은 drain이 FIFO로 이어서 처리한다.
                    queue.running = true;
                    shouldSchedule.set(queue);
                }
            }
            return queue;
        });

        if (!accepted.get()) {
            capacity.release();
            recordRejection(1);
            return false;
        }

        UserQueue queue = shouldSchedule.get();
        if (queue != null && !schedule(userId, queue)) {
            return false;
        }

        // true는 전송 완료가 아니라 "비동기 큐가 작업을 접수했다"는 뜻이다.
        return true;
    }

    /** 테스트와 운영 종료 확인에서 현재 실행·대기 작업이 모두 빠질 때까지 기다린다. */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (executor.getActiveCount() == 0 && queuedTasks.get() == 0) {
                return true;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return executor.getActiveCount() == 0 && queuedTasks.get() == 0;
    }

    @Override
    public void destroy() {
        // DisposableBean 덕분에 스프링 컨텍스트가 종료될 때 자동 호출된다.
        accepting.set(false);
        executor.shutdown();
        boolean interrupted = false;

        try {
            if (!executor.awaitTermination(shutdownAwait.toNanos(), TimeUnit.NANOSECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            interrupted = true;
            executor.shutdownNow();
        }

        int discarded = queues.entrySet().stream()
                .mapToInt(entry -> discardQueue(entry.getKey(), entry.getValue()))
                .sum();
        if (discarded > 0) {
            recordRejection(discarded);
            log.warn("애플리케이션 종료 중 알림 SSE 작업 {}건 폐기", discarded);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadPoolExecutor createExecutor(NotificationDeliveryProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                // core와 max를 같은 값으로 둔 고정 크기 풀이다. 부하가 커져도 스레드가 무한히 늘지 않는다.
                properties.threads(),
                properties.threads(),
                0L,
                TimeUnit.MILLISECONDS,
                // 메모리 보호를 위한 유한 물리 큐다. 실제 알림 총량은 Semaphore가 같은 상한으로 센다.
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                runnable -> Thread.ofPlatform()
                        .name("notification-delivery-" + threadNumber.getAndIncrement())
                        // 종료 대기 뒤에도 소켓 쓰기가 인터럽트에 반응하지 않으면 JVM 종료를 막지 않는다.
                        .daemon(true)
                        .unstarted(runnable),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private boolean schedule(long userId, UserQueue queue) {
        try {
            executor.execute(() -> drain(userId, queue));
            return true;
        } catch (RejectedExecutionException e) {
            int discarded = discardQueue(userId, queue);
            recordRejection(discarded);
            return false;
        }
    }

    private void drain(long userId, UserQueue queue) {
        // 한 작업 스레드가 이 회원의 큐를 앞에서부터 끝까지 비운다. 같은 회원에 drain을 둘 이상
        // 만들지 않으므로 새 알림과 unread-count 이벤트가 제출된 순서를 유지한다.
        while (true) {
            Runnable task;
            synchronized (queue) {
                task = queue.tasks.pollFirst();
                if (task != null) {
                    queuedTasks.decrementAndGet();
                }
            }

            if (task == null) {
                // 비어 있는 큐를 맵에 계속 두면 한 번 방문한 모든 회원 ID가 메모리에 영구히 남는다.
                // 다만 비었다고 확인하는 순간 새 작업이 들어올 수 있어 compute와 같은 잠금으로 제거한다.
                AtomicBoolean removed = new AtomicBoolean();
                queues.computeIfPresent(userId, (id, current) -> {
                    if (current != queue) {
                        return current;
                    }
                    synchronized (queue) {
                        if (queue.tasks.isEmpty()) {
                            queue.running = false;
                            removed.set(true);
                            return null;
                        }
                        return queue;
                    }
                });

                if (removed.get() || queues.get(userId) != queue) {
                    return;
                }
                continue;
            }

            try {
                task.run();
            } catch (RuntimeException e) {
                // 한 SSE 작업의 구현 결함이 같은 회원 큐를 영구히 멈추게 두지 않는다.
                log.error("알림 SSE 비동기 작업 실패, 회원 {}", userId, e);
            } finally {
                capacity.release();
                completed.increment();
            }
        }
    }

    private int discardQueue(long userId, UserQueue queue) {
        AtomicInteger discarded = new AtomicInteger();

        queues.computeIfPresent(userId, (id, current) -> {
            if (current != queue) {
                return current;
            }
            synchronized (queue) {
                int count = queue.tasks.size();
                queue.tasks.clear();
                queue.running = false;
                discarded.set(count);
                queuedTasks.addAndGet(-count);
                capacity.release(count);
                return null;
            }
        });
        return discarded.get();
    }

    private Counter taskCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(TASKS)
                .description("알림 SSE 전용 실행기의 작업 결과별 횟수")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private void recordRejection(int count) {
        if (count <= 0) {
            return;
        }
        rejected.increment(count);
        long total = rejectionCount.addAndGet(count);

        // 포화 때 요청마다 로그를 쓰면 그 I/O가 새 병목이 된다. 첫 건과 누적 천 건마다만 남긴다.
        if (total == count || total / 1_000 != (total - count) / 1_000) {
            log.warn("알림 SSE 전용 큐 포화 또는 종료로 작업 거절, 누적 {}건", total);
        }
    }

    private static final class UserQueue {
        // ArrayDeque 자체는 스레드 안전하지 않으므로 모든 접근은 synchronized(queue) 안에서만 한다.
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        // 이 큐를 담당하는 drain이 예약 또는 실행 중인지 나타낸다.
        private boolean running;
    }
}
