package com.softeer.race.auctionlist.application;

import com.softeer.race.bid.domain.BidAccepted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 목록을 갱신하는 것은 목록의 일이라 여기서 받는다, 경매방도 자기 몫을 따로 받는다
@Slf4j
@Component
@RequiredArgsConstructor
class AuctionListRefreshListener {

    private final AuctionListStreamService auctionListStreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidAccepted(BidAccepted event) {
        try {
            auctionListStreamService.broadcastCard(event.auctionId());
        } catch (Exception e) {
            // 안 잡아도 커밋한 쪽은 멀쩡하다, 이 리스너는 afterCompletion 훅으로 배달돼 스프링이 삼킨다
            // 잡는 이유는 무엇이 왜 실패했는지 여기서만 남길 수 있어서다
            log.warn("입찰 뒤 목록 카드 방송 실패, 경매 {}", event.auctionId(), e);
        }
    }
}
