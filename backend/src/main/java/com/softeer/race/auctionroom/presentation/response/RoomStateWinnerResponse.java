package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.common.domain.MaskedName;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "낙찰자, 방 전체에 같게 나가 본인 여부가 없고 확정 전에는 키는 있고 값이 null 이다")
public record RoomStateWinnerResponse(
        @Schema(description = "가운데를 마스킹한 낙찰자 이름", example = "이*호")
        String name
) {

    // 낙찰 확정 전에는 낙찰자가 아예 없다, 그 판단을 호출부에 흩지 않고 여기서 한다
    static RoomStateWinnerResponse from(MaskedName name) {
        return name == null ? null : new RoomStateWinnerResponse(name.value());
    }
}