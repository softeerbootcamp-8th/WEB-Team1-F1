package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.PricePoint;

import java.time.LocalDateTime;

/**
 * 가격 곡선의 점 하나, 내 입찰인지까지 판정된 상태
 */
public record PricePointView(
        LocalDateTime at,
        long amount,
        boolean mine
) {

    static PricePointView of(PricePoint point, long viewerId) {
        return new PricePointView(point.bidAt(), point.amount(), point.isMine(viewerId));
    }
}
