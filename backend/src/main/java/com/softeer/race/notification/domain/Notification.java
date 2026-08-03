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

    private Notification(User user, NotificationType type, Long referenceId) {
        // 링크를 만들 수 있는 조합인지 저장 전에 확인한다. 결과는 쓰지 않고 버린다 — 조회에서 처음
        // 걸리면 잘못 저장된 한 건이 목록 응답 전체를 500으로 만든다
        type.linkTo(referenceId);

        this.user = user;
        this.type = type;
        this.message = type.defaultMessage();
        this.isRead = false;
        this.referenceId = referenceId;
    }

    /**
     * 해당 종류의 기본 문구를 사용해 읽지 않은 알림을 만든다.
     * <p>
     * 문구는 발행 당시의 내용을 보존하기 위해 저장하고, 링크는 화면 주소 변경을 반영할 수 있도록
     * 저장하지 않고 {@link NotificationType}과 참조값으로 조회 시점에 만든다.
     */
    public static Notification create(User user, NotificationType type, Long referenceId) {
        return new Notification(user, type, referenceId);
    }
}
