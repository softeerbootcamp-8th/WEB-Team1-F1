package com.softeer.race.bid.application.dto;

import com.softeer.race.bid.domain.AuctionBidSnapshot;

/** 접수 결과(응답용)와 커밋된 사본(게이트용)을 가른다 - 응답 DTO 에 게이트 내부 값이 새지 않게. */
public record BidPlaced(BidPlaceInfo info, AuctionBidSnapshot snapshot) {
}
