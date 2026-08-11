package com.softeer.race.notification.application;

import com.softeer.race.notification.domain.Notification;
import com.softeer.race.notification.domain.NotificationContent;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.domain.NotificationType;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발행 — 저장하고, 커밋 뒤에 전달되도록 사건을 남긴다
 * <p>
 * 조회·읽음 처리를 담은 {@link NotificationService} 와 나눠 둔다. 발행을 부르는 쪽은 경매·거래처럼
 * 알림 도메인 밖이라, 그쪽에 목록 조회나 읽음 처리까지 보일 이유가 없다. 팀 코드가 AuctionCloser ·
 * AuctionStarter 로 협력자를 쪼개 둔 것과 같은 결이고, 남에게 넘길 접점도 이 클래스 하나로 좁아진다.
 */
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 알림 한 건 발행
     * <p>
     * <b>호출자의 트랜잭션에 참여한다</b> (REQUIRES_NEW 를 쓰지 않는다). 낙찰이 롤백되면 낙찰 알림도
     * 없어야 하고, 전달 시점도 그 호출자의 커밋을 기준으로 잡혀야 한다.
     * <p>
     * 회원 엔티티가 아니라 식별자를 받는다. 호출자는 대개 식별자만 들고 있고, 프록시로 참조만 걸면
     * FK 컬럼만 쓰이므로 회원 조회가 추가로 나가지 않는다. 없는 식별자면 저장에서 FK 위반으로
     * 터지는데, 사용자가 고칠 수 있는 문제가 아니라 발행한 쪽이 잘못 부른 것이라 감싸지 않는다.
     * <p>
     * 안 읽은 건수를 여기서 세어 담는다. 커밋 뒤에 세면 이미 끝난 트랜잭션 밖에서 커넥션을 다시
     * 얻어야 하고, 알림 한 건마다 그러면 열려 있는 구독 수만큼 커넥션 요구가 늘어난다. 식별자가
     * IDENTITY 라 save 시점에 INSERT 가 나가므로, 여기서 센 값에는 방금 저장한 건이 포함된다.
     */
    @Transactional
    public void publish(long userId, NotificationType type, Long referenceId) {
        publishInternal(userId, NotificationContent.defaultOf(type), referenceId);
    }

    /**
     * 발행 시점 값이 박힌 문구로 알림 한 건 발행 — 트랜잭션·참조규칙은 {@link #publish} 와 같다.
     * 문구를 조회할 때 조립하지 않는 이유는 {@link NotificationContent} 참고.
     */
    @Transactional
    public void publishContent(
            long userId, NotificationContent content, Long referenceId) {
        publishInternal(userId, content, referenceId);
    }

    private void publishInternal(
            long userId, NotificationContent content, Long referenceId) {
        Notification notification = notificationRepository.save(
                Notification.create(
                        userRepository.getReferenceById(userId),
                        content,
                        referenceId));

        NotificationPush push = new NotificationPush(
                NotificationRow.from(notification),
                notificationRepository.countUnread(userId));
        // 저장이 확정되기 전에는 내보내지 않는다, 커밋 여부와 전달 시점 판단은 NotificationPusher가 맡는다
        eventPublisher.publishEvent(new NotificationPublished(userId, push));
    }
}
