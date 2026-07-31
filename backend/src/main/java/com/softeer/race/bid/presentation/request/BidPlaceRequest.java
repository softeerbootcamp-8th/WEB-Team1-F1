package com.softeer.race.bid.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(description = "입찰 요청")
public record BidPlaceRequest(

        // 금액 규칙(현재가 + 상승가 배수)은 구간표를 알아야 판정할 수 있어 서버가 본다
        // 여기서는 형식만 거른다, 0과 음수는 어떤 경매에서도 유효하지 않다
        @Schema(description = "입찰 금액, 첫 입찰은 시작가 이상이고 이후는 현재가에 최저 상승가를 더한 배수여야 한다",
                example = "24850000")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        long amount
) {
}
