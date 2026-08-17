package com.softeer.race.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 알림 SSE 전용 실행기의 유한 자원 경계.
 *
 * <p>{@link ConfigurationProperties} 때문에 {@code application.yml}의
 * {@code notification.delivery.executor.*} 값이 이 레코드에 바인딩된다. 값을 생략하면 아래 기본값을
 * 사용하고, 생성자에서 범위를 검증하므로 잘못된 설정은 애플리케이션 시작 시 즉시 실패한다.</p>
 *
 * <p>기본값은 t3.micro 1대에서 시작하기 위한 보수적인 출발점이지 영구적인 정답이 아니다. 로컬과
 * 실제 인스턴스 부하 테스트에서 활성 스레드, 최대 대기 작업, 거절 횟수와 메모리를 확인해 조정한다.</p>
 */
@ConfigurationProperties(prefix = "notification.delivery.executor")
public record NotificationDeliveryProperties(
        Integer threads,
        Integer queueCapacity,
        Integer perUserQueueCapacity,
        Duration shutdownAwait
) {

    // SSE send는 CPU 계산보다 상대가 받아 가기를 기다리는 블로킹 I/O가 많다. 2 vCPU와 같은 2개보다
    // 약간의 겹침을 허용하되 1GiB 서버에서 스레드를 과도하게 만들지 않는 시작값으로 4개를 둔다.
    private static final int DEFAULT_THREADS = 4;

    // 순간 폭주를 흡수하되 작업 객체가 무한히 쌓여 1GiB 힙을 잠식하지 못하게 하는 전체 상한이다.
    private static final int DEFAULT_QUEUE_CAPACITY = 1_024;

    // 느리거나 탭을 많이 연 회원 한 명이 전체 1,024개를 독점하지 못하도록 전체의 1/4만 허용한다.
    private static final int DEFAULT_PER_USER_QUEUE_CAPACITY = 256;

    // 배포 종료를 무한정 늦추지 않으면서 짧은 대기열은 비울 수 있게 주는 유예 시간이다.
    // 끝내 못 보낸 SSE는 버려도 알림 DB가 남아 다음 조회에서 복구된다.
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
