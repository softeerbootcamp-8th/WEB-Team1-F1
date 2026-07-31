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

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    private Long referenceId;

    /**
     * 읽지 않은 상태의 알림 생성
     */
    public static Notification of(User user, NotificationType type, String message, Long referenceId) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.message = message;
        notification.isRead = false;
        notification.referenceId = referenceId;
        return notification;
    }
}
