package com.softeer.race.notification.application;

/**
 * 알림 한 건이 저장됐다는 사건
 * <p>
 * <b>엔티티를 담지 않고 값만 담는다.</b> 받는 쪽은 커밋 뒤에 도는데 그때 지연 로딩을 하면
 * 트랜잭션이 이미 끝나 있어 터진다. OSIV 를 꺼 둬서 웹 요청도 예외가 아니다.
 */
public record NotificationPublished(long userId, NotificationPush push) {
}