package com.softeer.race.bid.presentation.response;

import com.softeer.race.bid.domain.BidIncrementTable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "최저 입찰 상승가 구간표")
public record BidIncrementResponse(

        @Schema(description = "구간 목록, 하한 오름차순")
        List<BidIncrementBandResponse> bands
) {
    public static BidIncrementResponse from(BidIncrementTable table) {
        return new BidIncrementResponse(
                table.bands().stream()
                        .map(BidIncrementBandResponse::from)
                        .toList());
    }
}
