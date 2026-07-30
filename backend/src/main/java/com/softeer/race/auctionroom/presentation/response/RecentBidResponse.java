package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RecentBidView;
import com.softeer.race.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "호가 한 건")
public record RecentBidResponse(
        @Schema(description = "가운데를 마스킹한 입찰자 이름", example = "김*현")
        String name,

        @Schema(description = "입찰자 역할, 배지 문구는 화면이 정한다", example = "DEALER")
        Role role,

        @Schema(description = "입찰 금액", example = "12500000")
        long amount,

        @Schema(description = "입찰 시각", example = "2026-08-03T20:44:31")
        LocalDateTime bidAt,

        @Schema(description = "조회한 사람이 넣은 호가인지")
        boolean mine
) {

    static RecentBidResponse from(RecentBidView bid) {
        return new RecentBidResponse(bid.bidderName(), bid.role(), bid.amount(), bid.bidAt(), bid.mine());
    }
}