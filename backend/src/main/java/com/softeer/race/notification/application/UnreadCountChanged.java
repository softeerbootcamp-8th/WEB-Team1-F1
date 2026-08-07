package com.softeer.race.notification.application;

/**
 * 어떤 회원의 안 읽은 건수가 달라졌다는 사건
 * <p>
 * 알림이 저장됐다는 사건과 나눠 둔다. 저장은 목록에 줄이 하나 느는 일이지만 이쪽은 이미 있던
 * 줄의 읽음이 바뀐 것뿐이라, 실어 보낼 것도 받는 화면이 할 일도 다르다.
 * <p>
 * <b>건수를 담아 보낸다.</b> 커밋 뒤에 세면 끝난 트랜잭션 밖에서 커넥션을 다시 얻어야 하고,
 * 열려 있는 구독 수만큼 그 요구가 늘어난다. NotificationPublisher 가 건수를 미리 세는 것과 같은 이유다.
 */
public record UnreadCountChanged(long userId, long unreadCount) {
}