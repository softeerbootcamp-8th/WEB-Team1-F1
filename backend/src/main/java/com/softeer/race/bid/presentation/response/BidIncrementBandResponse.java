package com.softeer.race.bid.presentation.response;

import com.softeer.race.bid.domain.BidIncrementBand;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "가격 구간별 최저 입찰 상승가")
public record BidIncrementBandResponse(

        @Schema(description = "구간 하한, 이 금액부터 적용된다. 상한은 다음 구간의 하한이다", example = "5000000")
        long minPrice,

        @Schema(description = "이 구간의 최저 상승가, 입찰 금액은 이 값의 배수여야 한다", example = "50000")
        long increment
) {
    public static BidIncrementBandResponse from(BidIncrementBand band) {
        return new BidIncrementBandResponse(band.getMinPrice(), band.getIncrement());
    }
}
