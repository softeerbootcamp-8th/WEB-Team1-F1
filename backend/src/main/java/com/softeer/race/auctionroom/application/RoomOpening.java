package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.domain.VehicleSummary;

import java.time.LocalDateTime;

/**
 * 아직 열리지 않은 경매방의 안내, 남은 시간은 입장 가능 시각과 서버 시각의 차이로 화면이 센다
 */
public record RoomOpening(
        long auctionId,
        RoomPhase phase,
        VehicleSummary vehicle,
        long startPrice,
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime serverTime
) {

    /**
     * 한 번 읽어 온 상세와 기준 시각으로 안내를 조립한다
     */
    static RoomOpening of(AuctionRoomDetail detail, LocalDateTime now) {
        return new RoomOpening(
                detail.auctionId(),
                detail.phaseAt(now),
                detail.vehicle(),
                detail.startPrice(),
                detail.roomOpenAt(),
                detail.startTime(),
                now
        );
    }
}
