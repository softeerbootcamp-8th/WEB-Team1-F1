package com.softeer.race.bid.domain;

import com.softeer.race.common.exception.BusinessException;

import java.time.LocalDateTime;

import static com.softeer.race.bid.exception.BidErrorCode.SELLER_CANNOT_BID;

/**
 * 잠금 앞 판정에 쓰는 경매 값의 사본.
 */
public record AuctionBidSnapshot(
        long sellerId,
        long startPrice,
        Long currentPrice,            // 첫 입찰 전이면 null
        LocalDateTime startTime,
        LocalDateTime currentEndTime) {

    /**
     * 이 사본만으로 반드시 떨어진다고 말할 수 있는 입찰이면 거절한다.
     */
    public void rejectIfDoomed(
            BidIncrementTable table, long bidderId, long amount, LocalDateTime now) {

        // 판매자는 불변이라 사본이 낡아도, 경매가 진행 중이 아니어도 판정을 포기할 이유가 없다.
        if (sellerId == bidderId) {
            throw new BusinessException(SELLER_CANNOT_BID);
        }

        // 진행 중이 아니면 거절이 아니라 판정 포기다 - 단계 사유(AUCTION_NOT_LIVE)는 잠긴 행이 낸다.
        if (now.isBefore(startTime) || !now.isBefore(currentEndTime)) {
            return;
        }

        table.ruleFor(startPrice, currentPrice).validateMinimum(amount);
    }
}
