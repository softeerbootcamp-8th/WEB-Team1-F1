package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomOpening;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "아직 열리지 않은 경매방 안내")
public record RoomOpeningResponse(
        @Schema(description = "경매 식별자", example = "1")
        long auctionId,

        @Schema(description = "경매 차량")
        VehicleResponse vehicle,

        @Schema(description = "시작가", example = "10000000")
        long startPrice,

        @Schema(description = "방에 들어갈 수 있게 되는 시각, 입찰 시작 30분 전이다",
                example = "2026-08-03T20:00:00")
        LocalDateTime openAt,

        @Schema(description = "입찰이 시작되는 시각", example = "2026-08-03T20:30:00")
        LocalDateTime startAt,

        @Schema(description = "응답을 만든 서버 시각(KST), 남은 시간은 입장 가능 시각과의 차이로 센다",
                example = "2026-08-03T19:45:12")
        LocalDateTime serverTime
) {

    public static RoomOpeningResponse from(RoomOpening opening) {
        return new RoomOpeningResponse(
                opening.auctionId(),
                VehicleResponse.from(opening.vehicle()),
                opening.startPrice(),
                opening.openAt(),
                opening.startAt(),
                opening.serverTime());
    }
}
