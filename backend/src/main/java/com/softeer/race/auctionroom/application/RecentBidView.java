package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 호가창 한 줄, 내 입찰인지까지 판정된 상태
 */
public record RecentBidView(
        String bidderName,
        Role role,
        long amount,
        LocalDateTime bidAt,
        boolean mine
) {

    static RecentBidView of(RecentBid bid, long viewerId) {
        return new RecentBidView(
                bid.bidderName().value(),
                bid.role(),
                bid.amount(),
                bid.bidAt(),
                bid.isMine(viewerId));
    }
}
