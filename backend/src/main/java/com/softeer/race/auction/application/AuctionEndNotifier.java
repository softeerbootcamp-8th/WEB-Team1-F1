package com.softeer.race.auction.application;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_ENDED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_FAILED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_SOLD;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;

import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.notification.application.NotificationPublisher;
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
        // 판매자가 없는 경매는 사용자가 고칠 수 있는 문제가 아니라 데이터가 깨진 것이다
        long sellerId = auctionRepository.findSellerIdById(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "종료한 경매의 판매자를 찾을 수 없다, 경매 %d".formatted(auctionId)));

        // 유찰은 입찰이 0건이라는 뜻이라 알릴 상대가 판매자뿐이다, 입찰자 조회도 하지 않는다
        if (winnerId == null) {
            notificationPublisher.publish(sellerId, AUCTION_FAILED, auctionId);
            return;
        }

        notificationPublisher.publish(sellerId, AUCTION_SOLD, auctionId);
        notificationPublisher.publish(winnerId, AUCTION_WON, dealId);

        // 한 건씩 발행한다. 안 읽은 건수는 회원마다 달라서 묶어도 세는 횟수가 줄지 않는다.
        // 참여자가 열 명을 넘어가면 발행 횟수와 전송 시간을 #126 에서 함께 본다
        for (long bidderId : bidRepository.findOtherBidderIds(auctionId, winnerId)) {
            notificationPublisher.publish(bidderId, AUCTION_ENDED, auctionId);
        }
    }
}
