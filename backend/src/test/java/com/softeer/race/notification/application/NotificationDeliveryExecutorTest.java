package com.softeer.race.notification.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryExecutorTest {

    @Test
    @DisplayName("같은 회원에게 제출한 작업은 순서를 지켜 하나씩 실행한다")
    void preservesOrderForSameUser() {
        NotificationDeliveryExecutor executor = executor(4, 256);
        List<Integer> delivered = new CopyOnWriteArrayList<>();

        try {
            IntStream.range(0, 100)
                    .forEach(sequence -> assertThat(executor.execute(7L, () -> delivered.add(sequence))).isTrue());

            assertThat(executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(delivered).containsExactlyElementsOf(IntStream.range(0, 100).boxed().toList());
        } finally {
            executor.destroy();
        }
    }

    @Test
    @DisplayName("느린 회원과 다른 회원은 서로 막지 않는다")
    void isolatesDifferentUsers() throws Exception {
        NotificationDeliveryExecutor executor = executor(2, 4);
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch normalCompleted = new CountDownLatch(1);

        try {
            executor.execute(0L, () -> {
                slowStarted.countDown();
                await(releaseSlow);
            });
            assertThat(slowStarted.await(1, TimeUnit.SECONDS)).isTrue();

            // 0과 2는 2개 해시 스트라이프 방식이라면 충돌한다. 회원별 논리 큐는 영향을 받지 않는다.
            executor.execute(2L, normalCompleted::countDown);

            assertThat(normalCompleted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseSlow.countDown();
            assertThat(executor.awaitIdle(Duration.ofSeconds(1))).isTrue();
        } finally {
            releaseSlow.countDown();
            executor.destroy();
        }
    }

    @Test
    @DisplayName("유한 큐가 차면 호출자에서 실행하지 않고 새 SSE 작업을 거절한다")
    void rejectsWithoutRunningOnCallerWhenQueueIsFull() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationDeliveryExecutor executor = executor(1, 1, meterRegistry);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();

        try {
            assertThat(executor.execute(1L, () -> {
                running.countDown();
                await(release);
            })).isTrue();
            assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.execute(1L, () -> { })).isTrue();

            assertThat(executor.execute(1L, () -> rejectedTaskRan.set(true))).isFalse();
            assertThat(rejectedTaskRan).isFalse();
            assertThat(taskCount(meterRegistry, "submitted")).isEqualTo(3.0);
            assertThat(taskCount(meterRegistry, "rejected")).isEqualTo(1.0);

            release.countDown();
            assertThat(executor.awaitIdle(Duration.ofSeconds(1))).isTrue();
            assertThat(taskCount(meterRegistry, "completed")).isEqualTo(2.0);
        } finally {
            release.countDown();
            executor.destroy();
        }
    }

    private NotificationDeliveryExecutor executor(int threads, int perUserQueueCapacity) {
        return executor(threads, perUserQueueCapacity, new SimpleMeterRegistry());
    }

    private NotificationDeliveryExecutor executor(
            int threads,
            int perUserQueueCapacity,
            SimpleMeterRegistry meterRegistry) {
        return new NotificationDeliveryExecutor(
                new NotificationDeliveryProperties(
                        threads,
                        threads * (perUserQueueCapacity + 1),
                        perUserQueueCapacity,
                        Duration.ofSeconds(1)),
                meterRegistry);
    }

    private double taskCount(SimpleMeterRegistry meterRegistry, String outcome) {
        return meterRegistry.get(NotificationDeliveryExecutor.TASKS)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
