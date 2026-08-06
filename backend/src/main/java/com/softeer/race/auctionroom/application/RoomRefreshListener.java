package com.softeer.race.auctionroom.application;

import com.softeer.race.auction.domain.AuctionStarted;
import com.softeer.race.bid.domain.BidAccepted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 방을 갱신하는 것은 경매방의 일이라 여기서 받는다
// 반응이 모두 같아 경매방이 무엇에 반응하는지 읽는다
@Slf4j
@Component
@RequiredArgsConstructor
class RoomRefreshListener {

    private final AuctionRoomStreamService auctionRoomStreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidAccepted(BidAccepted event) {
        refresh(event.auctionId(), "입찰");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionStarted(AuctionStarted event) {
        refresh(event.auctionId(), "경매 시작");
    }

    private void refresh(long auctionId, String cause) {
        try {
            auctionRoomStreamService.refresh(auctionId);
        } catch (Exception e) {
            // 커밋은 이미 끝났다, 여기서 던지면 성공한 일이 호출자에게 실패로 보인다
            log.warn("{} 뒤 경매방 갱신 실패, 경매 {}", cause, auctionId, e);
        }
    }
}