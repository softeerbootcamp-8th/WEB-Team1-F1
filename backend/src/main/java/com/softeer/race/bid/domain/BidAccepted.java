package com.softeer.race.bid.domain;

/**
 * 입찰이 성립해 경매의 현재가와 마감이 갱신된 사건
 */
public record BidAccepted(long auctionId) {
}
