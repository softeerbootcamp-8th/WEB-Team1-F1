package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "낙찰자, 방 전체에 같게 나가므로 본인 여부가 없다")
public record RoomStateWinnerResponse(
        @Schema(description = "가운데를 마스킹한 낙찰자 이름", example = "이*호")
        String name
) {

    // 낙찰 확정 전에는 낙찰자가 아예 없다, 그 판단을 호출부에 흩지 않고 여기서 한다
    static RoomStateWinnerResponse from(RoomState state) {
        return state.winnerName() == null
                ? null
                : new RoomStateWinnerResponse(state.winnerName().value());
    }
}