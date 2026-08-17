package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.AuctionStartAlertSubscriptionRepository;
import com.softeer.race.auction.domain.AuctionStartAlertTarget;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.notification.domain.NotificationContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시작 알림 신청을 읽어 알림을 발행하고 신청을 정리한다
 * <p>
 * 후보를 뽑고 경매 단위로 묶는 일은 스케줄러가 한다. 트랜잭션을 여기서 여는 이유는 한 클래스 안에서
 * 자기 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 열리지 않기 때문이다 —
 * AuctionProgressScheduler 와 AuctionStarter 가 나뉘어 있는 것과 같은 이유다.
 */
@Service
@RequiredArgsConstructor
public class AuctionStartAlertNotifier {

    private final AuctionStartAlertSubscriptionRepository subscriptionRepository;
    private final NotificationPublisher notificationPublisher;

    /**
     * 한 경매의 신청자들을 처리한다
     * <p>
     * <b>알림 저장과 신청 삭제가 한 트랜잭션이다.</b> 중간에 실패하면 둘 다 되돌아가 신청이 남고
     * 다음 주기에 다시 잡히며, 성공하면 신청이 사라져 다시 뽑히지 않는다. 알림이 두 번 저장되는
     * 경로가 없어지는 것이 이 묶음의 목적이다.
     * <p>
     * 이미 끝난 경매는 알림 없이 신청만 지운다. 서버가 오래 멈춰 한 주기에서 시작과 종료가 연달아
     * 처리된 경우인데, 그때 "시작되었습니다"를 보내면 끝난 경매를 방금 시작했다고 알리게 된다.
     * <p>
     * 이름을 notify 로 두지 않는다. Object.notify 와 오버로드로 나란히 서서 읽는 사람을 헷갈리게 한다.
     */
    @Transactional
    public void notifyStart(long auctionId, List<AuctionStartAlertTarget> targets) {
        for (AuctionStartAlertTarget target : targets) {
            if (!target.startedAndUnnotified()) {
                continue;
            }

            notificationPublisher.publishContent(
                    target.userId(),
                    NotificationContent.auctionStarted(target.vehicleName().display()),
                    auctionId);
        }

        // 신청 한 건씩 지우면 DELETE 가 신청자 수만큼 나간다, 뽑아 둔 식별자로 한 번에 지운다.
        // 벌크 삭제는 영속성 컨텍스트를 우회하므로, 이 뒤에 지운 신청을 다시 읽는 코드를 붙이면 안 된다.
        subscriptionRepository.deleteAllByIdInBatch(
                targets.stream().map(AuctionStartAlertTarget::subscriptionId).toList());
    }
}