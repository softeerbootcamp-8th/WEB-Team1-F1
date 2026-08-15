package com.softeer.race.auction.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record AuctionUpdateRequest(
        @Schema(description = "시작가(원)", example = "10000000")
        @NotNull @PositiveOrZero
        @Max(value = 1_000_000_000_000L, message = "시작가는 1조원을 넘을 수 없습니다.")
        Long startPrice,

        @Schema(description = "경매 시작 시각", example = "2026-07-28T12:00:00")
        @NotNull LocalDateTime startAt
) {
}
