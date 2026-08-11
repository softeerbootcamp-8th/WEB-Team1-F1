package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionClosed;
import com.softeer.race.auction.domain.AuctionRepository;
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

        // 낙찰자를 여기서 고르지 않는다. 경매가 현재가를 만든 사람을 그대로 낙찰자로 확정하므로
        // 낙찰가와 낙찰자가 서로 다른 입찰에서 나올 수 없다.
        auction.close(now);

        // 입찰이 한 건도 없었으면 비어 있고, 그때가 유찰이다
        User winner = auction.getWinner();

        // 확정 뒤, 알림 앞이어야 한다. 낙찰 알림이 거래 화면을 가리키려면 이 시점에 거래가 있어야 한다.
        Deal deal = winner == null ? null : dealCreator.create(auction, winner, now);

        // 확정이 실제로 일어났을 때만 알린다, 유찰도 화면이 알아야 하는 결과라 같은 사건으로 낸다
        eventPublisher.publishEvent(new AuctionClosed(auctionId));

        // 종료와 한 트랜잭션에 둔다. 알림만 남거나, 알림 없이 종료되는 경우를 만들지 않는다.
        // 낙찰자 알림만 경매가 아니라 거래를 가리키므로 거래 식별자를 함께 넘긴다
        auctionEndNotifier.notifyEnd(auctionId,
                winner == null ? null : winner.getId(),
                deal == null ? null : deal.getId());
    }
}
