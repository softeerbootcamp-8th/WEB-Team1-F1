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
 * <p>회원마다 논리 큐를 하나씩 두고, 공유 스레드 풀이 서로 다른 회원을 병렬 처리한다. 같은 회원의
 * 토스트 순서는 지키면서도 해시가 우연히 같은 정상 회원을 느린 회원 뒤에 세우지 않는다.</p>
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

    private final ThreadPoolExecutor executor;
    private final Map<Long, UserQueue> queues = new ConcurrentHashMap<>();
    private final Semaphore capacity;
    private final int perUserQueueCapacity;
    private final Duration shutdownAwait;
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

        if (!accepting.get() || !capacity.tryAcquire()) {
            recordRejection(1);
            return false;
        }

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
                properties.threads(),
                properties.threads(),
                0L,
                TimeUnit.MILLISECONDS,
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
        while (true) {
            Runnable task;
            synchronized (queue) {
                task = queue.tasks.pollFirst();
                if (task != null) {
                    queuedTasks.decrementAndGet();
                }
            }

            if (task == null) {
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
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean running;
    }
}
