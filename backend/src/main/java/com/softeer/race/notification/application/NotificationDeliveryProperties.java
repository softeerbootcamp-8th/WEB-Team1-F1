package com.softeer.race.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 알림 SSE 전용 실행기의 유한 자원 경계. */
@ConfigurationProperties(prefix = "notification.delivery.executor")
public record NotificationDeliveryProperties(
        Integer threads,
        Integer queueCapacity,
        Integer perUserQueueCapacity,
        Duration shutdownAwait
) {

    private static final int DEFAULT_THREADS = 4;
    private static final int DEFAULT_QUEUE_CAPACITY = 1_024;
    private static final int DEFAULT_PER_USER_QUEUE_CAPACITY = 256;
    private static final Duration DEFAULT_SHUTDOWN_AWAIT = Duration.ofSeconds(10);

    public NotificationDeliveryProperties {
        threads = threads != null ? threads : DEFAULT_THREADS;
        queueCapacity = queueCapacity != null ? queueCapacity : DEFAULT_QUEUE_CAPACITY;
        perUserQueueCapacity = perUserQueueCapacity != null
                ? perUserQueueCapacity
                : DEFAULT_PER_USER_QUEUE_CAPACITY;
        shutdownAwait = shutdownAwait != null ? shutdownAwait : DEFAULT_SHUTDOWN_AWAIT;

        if (threads < 1 || threads > 32) {
            throw new IllegalArgumentException(
                    "notification.delivery.executor.threads는 1~32여야 합니다. threads=" + threads);
        }
        if (perUserQueueCapacity < 1 || perUserQueueCapacity > queueCapacity) {
            throw new IllegalArgumentException(
                    "notification.delivery.executor.per-user-queue-capacity는 1 이상이고 전체 queue-capacity 이하여야 합니다."
                            + " perUserQueueCapacity=" + perUserQueueCapacity
                            + ", queueCapacity=" + queueCapacity);
        }
        if (queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException(
                    "notification.delivery.executor.queue-capacity는 1~10000이어야 합니다. queueCapacity="
                            + queueCapacity);
        }
        if (shutdownAwait.isNegative() || shutdownAwait.isZero()
                || shutdownAwait.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(
                    "notification.delivery.executor.shutdown-await는 0초 초과 1분 이하여야 합니다. shutdownAwait="
                            + shutdownAwait);
        }
    }
}
