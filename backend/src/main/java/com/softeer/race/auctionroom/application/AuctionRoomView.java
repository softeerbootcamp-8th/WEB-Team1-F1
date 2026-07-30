package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매방 조회 결과
 */
public record AuctionRoomView(
        long auctionId,
        RoomPhase phase,
        VehicleSummary vehicle,
        String thumbnailUrl,
        long startPrice,
        long currentPrice,
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime serverTime,
        int connectedCount,
        long bidderCount,
        long bidCount,
        String winnerName,
        boolean winnerIsMine,
        List<RecentBidView> recentBids
) {

    public static AuctionRoomView of(long auctionId, long viewerId, RoomPhase phase, AuctionRoomDetail detail,
                                     int connectedCount, BidStats stats, List<RecentBid> recentBids,
                                     LocalDateTime serverTime) {
        AuctionRoomSnapshot snapshot = detail.snapshot();

        return new AuctionRoomView(
                auctionId,
                phase,
                detail.vehicle(),
                detail.thumbnailUrl(),
                snapshot.startPrice(),
                snapshot.displayPrice(),
                snapshot.roomOpenAt(),
                snapshot.startTime(),
                snapshot.endTime(),
                serverTime,
                connectedCount,
                stats.bidderCount(),
                stats.bidCount(),
                detail.winnerName().map(MaskedName::value).orElse(null),
                detail.isWonBy(viewerId),
                recentBids.stream().map(bid -> RecentBidView.of(bid, viewerId)).toList()
        );
    }
}