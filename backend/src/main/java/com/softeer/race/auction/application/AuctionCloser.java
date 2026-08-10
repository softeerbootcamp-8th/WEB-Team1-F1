package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionClosed;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.bid.domain.Bid;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.user.domain.User;
import com.softeer.race.deal.application.DealCreator;
import com.softeer.race.deal.domain.Deal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionCloser {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AuctionEndNotifier auctionEndNotifier;
    private final DealCreator dealCreator;

    /**
     * 경매 한 건을 종료한다. 경매 하나가 한 트랜잭션이다.
     */
    @Transactional
    public void close(long auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "종료 후보로 뽑힌 경매를 찾을 수 없다, 경매 %d".formatted(auctionId)));

        LocalDateTime now = LocalDateTime.now(clock);

        // 후보를 뽑을 때 읽은 마감이 낡았을 수 있다. 마감 직전 입찰이 커밋되기 전에 조회가 돌면 연장 전 마감이 보이기 때문이다.
        // 잠금을 얻은 지금은 최신이므로 다시 판정한다.
        if (!auction.isClosableAt(now)) {
            return;
        }

        User topBidder = topBidderOf(auctionId);
        auction.close(topBidder, now);

        // 확정 뒤, 알림 앞이어야 한다. 낙찰 알림이 거래 화면을 가리키려면 이 시점에 거래가 있어야 한다.
        Deal deal = topBidder == null ? null : dealCreator.create(auction, topBidder, now);

        // 확정이 실제로 일어났을 때만 알린다, 유찰도 화면이 알아야 하는 결과라 같은 사건으로 낸다
        eventPublisher.publishEvent(new AuctionClosed(auctionId));

        // 종료와 한 트랜잭션에 둔다. 알림만 남거나, 알림 없이 종료되는 경우를 만들지 않는다.
        // 낙찰자 알림만 경매가 아니라 거래를 가리키므로 거래 식별자를 함께 넘긴다
        auctionEndNotifier.notifyEnd(auctionId,
                topBidder == null ? null : topBidder.getId(),
                deal == null ? null : deal.getId());
    }

    /**
     * 이 경매의 최고 입찰자, 입찰이 없으면 null 이고 유찰로 끝난다
     * <p>
     * 가장 최근 입찰을 최고가 입찰로 쓴다. 쿼리의 계약은 "가장 최근"이지 "최고가"가 아니고,
     * 둘이 같은 근거는 BidRule 이 현재가 + 상승가 이상만 통과시켜 금액이 단조 증가한다는 데 있다.
     * 입찰 취소나 대리입찰이 들어오면 그 전제가 깨지고, 깨지는 것은 쿼리가 아니라 낙찰 결과다.
     */
    private User topBidderOf(long auctionId) {
        return bidRepository.findFirstByAuctionIdOrderByIdDesc(auctionId)
                .map(Bid::getBidder)
                .orElse(null);
    }
}
