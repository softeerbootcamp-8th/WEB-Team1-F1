package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.ViewerCount;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경매방을 보고 있는 사람 수의 변화")
public record ViewerCountResponse(
        @Schema(description = "경매 식별자", example = "1")
        long auctionId,

        @Schema(description = "지금 방을 보고 있는 사람 수, 한 사람이 창을 여럿 열어도 하나로 센다", example = "12")
        int viewerCount
) {

    // 순번은 싣지 않는다, 낡은 것은 구독이 쓰기 전에 버리므로 화면이 알 필요가 없다
    public static ViewerCountResponse from(ViewerCount viewers) {
        return new ViewerCountResponse(viewers.auctionId(), viewers.viewerCount());
    }
}
