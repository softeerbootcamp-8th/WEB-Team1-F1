package com.softeer.race.auction.domain;

/**
 * 경매가 마감돼 낙찰이나 유찰로 확정된 사건
 */
public record AuctionClosed(long auctionId) {
}