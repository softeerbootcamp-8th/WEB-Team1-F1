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

    /**
     * 방금 저장한 알림을 전송에 실을 모양으로
     * <p>
     * 조회는 JPQL 생성자로 이 record 를 직접 만들지만, 발행 직후에는 엔티티가 손에 있다. 변환을
     * 여기 두어 조회 경로와 전송 경로가 같은 필드 구성을 쓰게 한다.
     */
    public static NotificationRow from(Notification notification) {
        return new NotificationRow(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReferenceId(),
                notification.getCreatedAt());
    }

    /** 알림을 눌렀을 때 갈 곳, 저장하지 않고 내보낼 때마다 만든다 */
    public String link() {
        return type.linkTo(referenceId);
    }
}