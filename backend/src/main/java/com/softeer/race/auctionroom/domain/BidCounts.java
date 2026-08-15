package com.softeer.race.auctionroom.domain;

/**
 * 입찰 건수와 입찰자 수, 방 조회와 스트림과 결과가 모두 싣는다
 */
public record BidCounts(
        long bidCount,
        long bidderCount
) {
}