package com.softeer.race.bid.domain;

import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 잠금을 잡기 전에 끝낼 수 있는 판정에 필요한 값들.
 */
public record BidPreCheck(
        Role role,
        Long sellerId,
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

    public boolean isSeller(long bidderId) {
        return sellerId == bidderId;
    }

    /**
     * 확실히 떨어질 금액이면 잠금 앞에서 거절한다.
     * 가격이 단조 증가하므로 낡은 현재가로 "너무 낮다"면 최신 값으로도 반드시 너무 낮다 — 오거절이 불가능하다.
     */
    public void rejectIfBelowMinimum(BidIncrementTable table, long amount, LocalDateTime now) {
        if (now.isBefore(startTime) || !now.isBefore(currentEndTime)) {
            return;
        }
        table.ruleFor(startPrice, currentPrice).validateMinimum(amount);
    }
}
