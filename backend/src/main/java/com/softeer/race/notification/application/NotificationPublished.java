package com.softeer.race.notification.application;

/**
 * 알림 한 건이 저장됐다는 사건
 * <p>
 * <b>엔티티를 담지 않고 값만 담는다.</b> 받는 쪽은 커밋 뒤에 도는데 그때 지연 로딩을 하면,
 * 웹 요청에서는 OSIV 덕에 성공하고 스케줄러 스레드에서는 터진다. 낙찰 알림의 발행 주체가 바로
 * 그 스케줄러라, 엔티티를 담으면 통합 테스트는 통과하고 배포하면 낙찰 알림만 실패한다.
 */
public record NotificationPublished(long userId, NotificationPush push) {
}