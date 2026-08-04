package com.softeer.race.notification.application;

import com.softeer.race.notification.domain.NotificationRow;

/**
 * 새 알림 한 건을 회원 채널로 밀어줄 때 실리는 내용
 * <p>
 * 알림과 안 읽은 건수를 함께 싣는다. 건수를 빼면 화면이 스스로 세야 해서 다른 탭에서 읽음 처리한
 * 뒤 값이 어긋나고, 알림을 빼면 화면이 목록을 다시 조회해야 해서 왕복이 늘고 그사이 상태가
 * 어긋나 보인다.
 * <p>
 * 건수는 저장 트랜잭션 안에서 세어 담는다. 커밋 뒤에 세면 이미 닫힌 트랜잭션 밖에서 커넥션을
 * 다시 얻어야 하는데, 알림 한 건마다 그러면 열려 있는 구독 수만큼 커넥션 요구가 늘어난다.
 */
public record NotificationPush(NotificationRow notification, long unreadCount) {
}