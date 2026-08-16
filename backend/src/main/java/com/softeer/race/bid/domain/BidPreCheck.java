package com.softeer.race.bid.domain;

import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 잠금을 잡기 전에 끝낼 수 있는 판정에 필요한 값들.
 */
public record BidPreCheck(
        Role role,
        String bidderRealName,
        Long sellerId,
        String vehicleModel,
        Long startPrice,
        Long currentPrice,
        LocalDateTime startTime,
        LocalDateTime currentEndTime) {

    public boolean hasAuction() {
        return startPrice != null;
    }

    public boolean isEvaluator() {
        return role == Role.EVALUATOR;
    }

    /** 잠금 앞 판정에 쓸 사본. 경매 값들을 언박싱하므로 hasAuction 확인 뒤에 불러야 한다. */
    public AuctionBidSnapshot toSnapshot() {
        return new AuctionBidSnapshot(sellerId, startPrice, currentPrice, startTime, currentEndTime);
    }
}
