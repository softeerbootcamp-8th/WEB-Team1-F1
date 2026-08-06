package com.softeer.race.auctionroom.application;

import com.softeer.race.bid.domain.BidAccepted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 방을 갱신하는 것은 경매방의 일이라 입찰 쪽이 부르지 않고 여기서 받는다
@Slf4j
@Component
@RequiredArgsConstructor
class BidAcceptedListener {

    private final AuctionRoomStreamService auctionRoomStreamService;

    // 기본값이지만 명시한다, 읽는 사람이 기본값을 외우고 있어야 하면 안 된다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidAccepted(BidAccepted event) {
        try {
            auctionRoomStreamService.refresh(event.auctionId());
        } catch (Exception e) {
            // 커밋은 이미 끝났다, 여기서 던지면 성공한 입찰이 호출자에게 실패로 보인다
            log.warn("입찰 뒤 경매방 갱신 실패, 경매 {}", event.auctionId(), e);
        }
    }
}
