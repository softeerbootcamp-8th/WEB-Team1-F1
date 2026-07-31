package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.AuctionRoomSnapshot;
import com.softeer.race.auctionroom.domain.MaskedName;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.domain.VehicleSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방에 있는 누구에게나 같은 경매방 현황, 브로드캐스트 단위
 */
public record RoomState(
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
        MaskedName winnerName,
        List<RoomStateBid> recentBids
) {

    /**
     * 한 번 읽어 온 값과 접속자 수로 방 현황을 조립한다
     */
    static RoomState of(RoomQueryResult result, int connectedCount) {
        AuctionRoomDetail detail = result.detail();
        AuctionRoomSnapshot snapshot = detail.snapshot();

        return new RoomState(
                detail.auctionId(),
                result.phase(),
                detail.vehicle(),
                detail.thumbnailUrl(),
                snapshot.startPrice(),
                snapshot.displayPrice(),
                snapshot.roomOpenAt(),
                snapshot.startTime(),
                snapshot.endTime(),
                result.serverTime(),
                connectedCount,
                result.stats().bidderCount(),
                result.stats().bidCount(),
                detail.winnerName().orElse(null),
                result.recentBids().stream().map(RoomStateBid::from).toList());
    }
}
