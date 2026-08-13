package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomResultView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "조회한 사람의 성적, 입찰한 적이 없으면 없다")
public record MyStandingResponse(
        @Schema(description = "내가 넣은 것 중 가장 높은 금액", example = "9300000")
        long highestAmount,

        @Schema(description = "입찰한 사람 중 내 순위, 1이면 내가 가장 높았다", example = "2")
        int rank
) {

    // 입찰하지 않은 사람에게 값 둘을 각각 null 로 주지 않는다, 낙찰자와 같은 규칙으로 통째로 비운다
    static MyStandingResponse from(RoomResultView view) {
        return view.viewerStanding() == null
                ? null
                : new MyStandingResponse(view.viewerStanding().highestAmount(), view.viewerStanding().rank());
    }
}
