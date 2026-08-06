package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.BidStats;
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
        long startPrice,
        long currentPrice,
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime serverTime,
        int connectedCount,
        BidStats stats,
        MaskedName winnerName,
        List<RoomStateBid> recentBids
) {

    /**
     * 한 번 읽어 온 값과 접속자 수로 방 현황을 조립한다
     */
    static RoomState of(RoomQueryResult result, int connectedCount) {
        AuctionRoomDetail detail = result.detail();

        return new RoomState(
                detail.auctionId(),
                result.phase(),
                detail.vehicle(),
                detail.startPrice(),
                detail.currentPrice(),
                detail.roomOpenAt(),
                detail.startTime(),
                detail.currentEndTime(),
                result.serverTime(),
                connectedCount,
                result.stats(),
                detail.winnerName().orElse(null),
                result.recentBids().stream().map(RoomStateBid::from).toList());
    }
}
