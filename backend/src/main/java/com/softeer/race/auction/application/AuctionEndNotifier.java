package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.AuctionEndNotificationContext;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.notification.domain.NotificationContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝난 경매의 참여자에게 결과를 알린다
 * <p>
 * AuctionCloser 에서 떼어 낸 이유. 낙찰 확정은 잠그고 다시 판정하고 상태를 바꾸는 짧은 코드인데,
 * 여기에 받을 사람을 네 갈래로 고르는 일이 들어오면 확정 로직이 알림에 묻힌다. 팀 코드가
 * AuctionCloser · AuctionStarter 로 협력자를 쪼개 둔 것과 같은 결이다.
 * <p>
 * <b>호출자의 트랜잭션에 참여한다</b> (REQUIRES_NEW 를 쓰지 않는다). 종료가 롤백되면 알림도 없어야
 * 하고, 반대로 알림만 남는 종료도 없어야 한다. 전달만 커밋 뒤로 미뤄지고, 전달 실패는 종료를
 * 흔들지 않는다 (NotificationPusher).
 */
@Service
@RequiredArgsConstructor
public class AuctionEndNotifier {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final NotificationPublisher notificationPublisher;

    /**
     * @param winnerId 낙찰자, 입찰이 없어 유찰로 끝났으면 null
     * @param dealId   낙찰로 열린 거래, 유찰이면 null. 낙찰자만 경매가 아니라 거래로 보낸다 —
     *                 결과 확인이 끝나면 경매방에는 볼 것이 없고 할 일은 거래 화면에 있다
     */
    @Transactional
    public void notifyEnd(long auctionId, Long winnerId, Long dealId) {
        AuctionEndNotificationContext context =
                auctionRepository.findEndNotificationContext(auctionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "종료한 경매의 알림 정보를 찾을 수 없다, 경매 %d"
                                        .formatted(auctionId)));

        // 유찰은 입찰자가 한 명도 없다는 뜻이므로 판매자만 알리고 입찰자 조회도 하지 않는다
        if (winnerId == null) {
            notificationPublisher.publishContent(
                    context.sellerId(),
                    NotificationContent.auctionFailed(context.vehicleModel()),
                    auctionId);
            return;
        }

        long finalPrice = context.finalPriceOrThrow(auctionId);

        notificationPublisher.publishContent(
                context.sellerId(),
                NotificationContent.auctionSold(
                        context.vehicleModel(), finalPrice),
                auctionId);

        notificationPublisher.publishContent(
                winnerId,
                NotificationContent.auctionWon(
                        context.vehicleModel(), finalPrice),
                dealId);

        for (long bidderId :
                bidRepository.findOtherBidderIds(auctionId, winnerId)) {
            notificationPublisher.publishContent(
                    bidderId,
                    NotificationContent.auctionEnded(
                            context.vehicleModel(), finalPrice),
                    auctionId);
        }
    }
}
