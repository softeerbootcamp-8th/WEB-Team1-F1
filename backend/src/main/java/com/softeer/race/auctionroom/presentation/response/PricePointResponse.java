package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.PricePointView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "가격이 오른 과정의 점 하나")
public record PricePointResponse(
        @Schema(description = "그 입찰이 들어온 시각", example = "2026-08-03T18:35:00")
        LocalDateTime at,

        @Schema(description = "그때 올라간 금액", example = "21000000")
        long amount,

        @Schema(description = "조회한 사람이 넣은 입찰인지")
        boolean mine
) {

    static PricePointResponse from(PricePointView view) {
        return new PricePointResponse(view.at(), view.amount(), view.mine());
    }
}
