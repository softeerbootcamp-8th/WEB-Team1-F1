package com.softeer.race.auctionroom.domain;

/**
 * 경매방 화면에 보이는 입찰 집계
 */
public record BidStats(
        long bidCount,
        long bidderCount
) {
}