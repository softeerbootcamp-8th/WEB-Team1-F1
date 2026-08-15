package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.BidPoint;

import java.time.LocalDateTime;

/**
 * 가격 곡선의 점 하나, 내 입찰인지와 마감을 밀어냈는지까지 판정된 상태
 */
public record BidPointView(
        LocalDateTime at,
        long amount,
        boolean mine,
        boolean extended
) {

    // 마감이 어느 입찰에 밀렸는지는 저장돼 있지 않아 경매 시작 시각으로 되짚어 묻는다
    static BidPointView of(BidPoint point, long viewerId, LocalDateTime startAt) {
        return new BidPointView(point.bidAt(), point.amount(), point.isMine(viewerId), point.extendsDeadline(startAt));
    }
}