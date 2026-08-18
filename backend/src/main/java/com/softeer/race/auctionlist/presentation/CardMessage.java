package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;

record CardMessage(AuctionCardInfo card) implements AuctionListMessage {

    @Override
    public long auctionId() {
        return card.auctionId();
    }

    // 현재가는 락 안에서 더 높은 금액만 받아 단조 증가한다, 낮은 값을 든 카드가 더 이른 시점을 읽은 것이다
    @Override
    public long mark() {
        return card.currentPrice();
    }
}
