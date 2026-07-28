package com.softeer.race.bid.presentation.response;

import com.softeer.race.bid.domain.BidIncrementPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "최저 입찰 상승가 구간표")
public record BidIncrementResponse(

        @Schema(description = "구간 목록, 하한 오름차순")
        List<BidIncrementTierResponse> tiers
) {
    public static BidIncrementResponse from(BidIncrementPolicy policy) {
        return new BidIncrementResponse(
                policy.tiers().stream()
                        .map(BidIncrementTierResponse::from)
                        .toList());
    }
}