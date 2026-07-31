package com.softeer.race.bid.presentation.response;

import com.softeer.race.bid.application.dto.BidPlaceInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "입찰 접수 결과")
public record BidPlaceResponse(
        @Schema(description = "성립한 입찰 식별자", example = "42")
        long bidId,

        @Schema(description = "성립한 금액, 곧 새 현재가다", example = "24850000")
        long amount,

        @Schema(description = "마감 시각, 마감 임박 입찰이면 연장되어 있다",
                example = "2026-08-03T21:00:30")
        LocalDateTime endAt,

        @Schema(description = "응답을 만든 서버 시각, 클라이언트 시계 보정에 쓴다",
                example = "2026-08-03T20:59:58")
        LocalDateTime serverTime
) {
    public static BidPlaceResponse from(BidPlaceInfo info) {
        return new BidPlaceResponse(info.bidId(), info.amount(), info.endAt(), info.serverTime());
    }
}
