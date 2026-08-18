package com.softeer.race.auctionlist.presentation;

record AudienceMessage(long auctionId, int viewerCount) implements AuctionListMessage {

    // 500ms 주기 하나에서만 나와 순서가 이미 보장된다, 접기만 하면 되고 가릴 것이 없다
    @Override
    public long mark() {
        return 0;
    }
}
