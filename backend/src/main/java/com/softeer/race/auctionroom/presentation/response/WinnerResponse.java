package com.softeer.race.auctionroom.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "낙찰자, 낙찰 확정 전에는 없다")
public record WinnerResponse(
        @Schema(description = "가운데를 마스킹한 낙찰자 이름", example = "이*호")
        String name,

        @Schema(description = "조회한 사람이 낙찰자인지")
        boolean mine
) {
}