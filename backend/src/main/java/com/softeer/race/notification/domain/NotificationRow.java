package com.softeer.race.notification.domain;

import java.time.LocalDateTime;

/**
 * 알림 목록 한 줄
 * <p>
 * 엔티티가 아니라 프로젝션으로 받는다. 엔티티로 받으면 user 가 지연 로딩이라 목록을 훑는 사이
 * 회원 조회가 건수만큼 따라붙는데, OSIV 가 켜져 있어 예외 없이 조용히 일어난다.
 */
public record NotificationRow(
        long id,
        NotificationType type,
        String message,
        boolean read,
        Long referenceId,
        LocalDateTime createdAt
) {

    /** 알림을 눌렀을 때 갈 곳, 저장하지 않고 내보낼 때마다 만든다 */
    public String link() {
        return type.linkTo(referenceId);
    }
}