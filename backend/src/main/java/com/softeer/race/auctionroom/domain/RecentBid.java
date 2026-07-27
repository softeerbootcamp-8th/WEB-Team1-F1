package com.softeer.race.auctionroom.domain;

import com.softeer.race.bid.domain.Bid;

import java.time.LocalDateTime;

/**
 * 호가창에 보이는 입찰 한 건
 */
public record RecentBid(
        MaskedName bidderName,
        long amount,
        LocalDateTime bidAt
) {

    public static RecentBid from(Bid bid) {
        return new RecentBid(
                new MaskedName(bid.getBidder().getNickname()),
                bid.getAmount(),
                bid.getCreatedAt());
    }
}