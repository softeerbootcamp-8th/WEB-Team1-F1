package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomSnapshot;
import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.auctionroom.domain.RoomPhase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매방 조회 결과
 */
public record AuctionRoomView(
        long auctionId,
        RoomPhase phase,
        long startPrice,
        long currentPrice,
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime serverTime,
        int connectedCount,
        int bidderCount,
        List<RecentBid> recentBids
) {

    public static AuctionRoomView of(long auctionId, RoomPhase phase, AuctionRoomSnapshot snapshot, int connectedCount,
                                     int bidderCount, List<RecentBid> recentBids, LocalDateTime serverTime) {
        return new AuctionRoomView(
                auctionId,
                phase,
                snapshot.startPrice(),
                snapshot.displayPrice(),
                snapshot.roomOpenAt(),
                snapshot.startTime(),
                snapshot.endTime(),
                serverTime,
                connectedCount,
                bidderCount,
                recentBids
        );
    }
}