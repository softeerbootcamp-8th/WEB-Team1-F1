package com.softeer.race.auction.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record AuctionCreateRequest(

        @Schema(description = "경매에 등록할 차량 ID", example = "1000")
        @NotNull Long vehicleId,

        @Schema(description = "시작가(원)", example = "10000000")
        @NotNull @PositiveOrZero Long startPrice,

        @Schema(description = "경매 시작 시각", example = "2026-07-28T12:00:00")
        @NotNull LocalDateTime startAt
) {
}
