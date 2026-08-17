package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.BidPointView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "가격이 오른 과정의 점 하나")
public record BidPointResponse(
        @Schema(description = "그 입찰이 들어온 시각", example = "2026-08-03T18:35:00")
        LocalDateTime bidAt,

        @Schema(description = "그 입찰의 금액", example = "21000000")
        long amount,

        @Schema(description = "조회한 사람이 넣은 입찰인지")
        boolean mine,

        @Schema(description = "이 입찰로 마감이 밀렸는지, 마감 임박에 들어온 입찰만 참이다")
        boolean extended
) {

    static BidPointResponse from(BidPointView view) {
        return new BidPointResponse(view.bidAt(), view.amount(), view.mine(), view.extended());
    }
}