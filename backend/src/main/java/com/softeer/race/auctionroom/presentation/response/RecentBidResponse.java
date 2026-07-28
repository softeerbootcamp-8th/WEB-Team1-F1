package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.domain.RecentBid;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "호가 한 건")
public record RecentBidResponse(
        @Schema(description = "가운데를 마스킹한 이름", example = "김*현")
        String bidderName,

        @Schema(description = "입찰 금액", example = "12500000")
        long amount,

        @Schema(description = "입찰 시각", example = "2026-08-03T20:44:31")
        LocalDateTime bidAt
) {

    public static RecentBidResponse from(RecentBid bid) {
        return new RecentBidResponse(bid.bidderName().value(), bid.amount(), bid.bidAt());
    }
}