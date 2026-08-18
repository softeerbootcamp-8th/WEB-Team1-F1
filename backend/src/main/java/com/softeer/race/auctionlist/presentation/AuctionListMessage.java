package com.softeer.race.auctionlist.presentation;

// 열린 구독으로 흘려보내는 한 건, 접기와 순서 판정에 필요한 것만 안다
sealed interface AuctionListMessage permits CardMessage, AudienceMessage {

    long auctionId();

    // 같은 칸에서 순서를 가리는 값, 큰 것이 나중이다
    long mark();
}
