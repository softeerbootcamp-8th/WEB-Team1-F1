package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.AuctionRoomView;
import com.softeer.race.auctionroom.application.RoomResultView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "낙찰자, 낙찰 확정 전에는 없다")
public record WinnerResponse(
        @Schema(description = "가운데를 마스킹한 낙찰자 이름", example = "이*호")
        String name,

        @Schema(description = "조회한 사람이 낙찰자인지")
        boolean mine
) {

    // 낙찰 확정 전에는 낙찰자가 아예 없다, 그 판단을 응답 둘에 흩지 않고 여기 모은다
    // 조회 결과를 그대로 받아 마스킹된 이름이 여기서 처음 문자열로 풀린다
    static WinnerResponse from(AuctionRoomView view) {
        return view.state().winnerName() == null
                ? null
                : new WinnerResponse(view.state().winnerName().value(), view.winnerIsMine());
    }

    static WinnerResponse from(RoomResultView view) {
        return view.winnerName() == null
                ? null
                : new WinnerResponse(view.winnerName().value(), view.winnerIsMine());
    }
}