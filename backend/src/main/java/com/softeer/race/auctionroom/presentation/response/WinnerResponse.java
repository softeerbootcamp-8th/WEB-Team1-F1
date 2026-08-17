package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.common.domain.MaskedName;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "낙찰자, 낙찰 확정 전에는 키는 있고 값이 null 이다")
public record WinnerResponse(
        @Schema(description = "가운데를 마스킹한 낙찰자 이름", example = "이*호")
        String name,

        @Schema(description = "조회한 사람이 낙찰자인지")
        boolean mine
) {

    // 낙찰 확정 전에는 낙찰자가 아예 없다, 그 판단을 응답 둘에 흩지 않고 여기 모은다
    // 마스킹된 이름이 여기서 처음 문자열로 풀린다
    static WinnerResponse from(MaskedName name, boolean mine) {
        return name == null ? null : new WinnerResponse(name.value(), mine);
    }
}