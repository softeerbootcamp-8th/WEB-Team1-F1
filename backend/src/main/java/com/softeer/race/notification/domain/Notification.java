package com.softeer.race.notification.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = NotificationContent.MAX_MESSAGE_LENGTH)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    private Long referenceId;

    private Notification(User user, NotificationContent content, Long referenceId) {
        // 잘못 저장된 한 건이 목록 전체를 깨뜨리지 않도록 저장 전에 링크 조합을 검사한다
        content.type().linkTo(referenceId);

        this.user = user;
        this.type = content.type();
        this.message = content.message();
        this.isRead = false;
        this.referenceId = referenceId;
    }

    /**
     * 종류의 기본 문구로 알림을 만든다.
     * 문구는 발행 당시 내용을 보존하고, 링크는 주소 변경을 반영할 수 있도록
     * 저장하지 않고 종류와 참조값으로 조회 시점에 만든다.
     */
    public static Notification create(
            User user, NotificationType type, Long referenceId) {
        return create(user, NotificationContent.defaultOf(type), referenceId);
    }

    /**
     * 발행 시점의 동적 값이 반영된 문구로 알림을 만든다.
     */
    public static Notification create(
            User user, NotificationContent content, Long referenceId) {
        return new Notification(user, content, referenceId);
    }
}
