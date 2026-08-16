package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.domain.RoomPhase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "실시간으로 밀어주는 경매방 현황, 방에 있는 모두에게 같은 값이다")
public record RoomStateResponse(
        @Schema(description = "경매 식별자", example = "1")
        long auctionId,

        @Schema(description = "방 단계, 이 이름들이 API 계약이라 값을 그대로 비교해도 된다", example = "LIVE")
        RoomPhase phase,

        @Schema(description = "현재가, 입찰이 없으면 시작가와 같다", example = "12500000")
        long currentPrice,

        @Schema(description = "마감 시각, 연장되면 뒤로 밀린다", example = "2026-08-03T21:00:00")
        LocalDateTime endAt,

        @Schema(description = "응답을 만든 서버 시각(KST), 클라이언트 시계 보정에 쓴다",
                example = "2026-08-03T20:45:12")
        LocalDateTime serverTime,

        @Schema(description = "지금 방을 보고 있는 사람 수, 한 사람이 창을 여럿 열어도 하나로 센다", example = "12")
        int viewerCount,

        @Schema(description = "지금까지 들어온 입찰 건수, 최근 호가 20건과 달리 전체를 센다", example = "37")
        long bidCount,

        @Schema(description = "지금까지 입찰한 사람 수", example = "4")
        long bidderCount,

        @Schema(description = "낙찰자, 낙찰 확정 전에는 키는 있고 값이 null 이다")
        RoomStateWinnerResponse winner,

        @Schema(description = "최근 호가, 최신순 최대 20건")
        List<RoomStateBidResponse> recentBids
) {

    public static RoomStateResponse from(RoomState state) {
        return new RoomStateResponse(
                state.auctionId(),
                state.phase(),
                state.currentPrice(),
                state.endAt(),
                state.serverTime(),
                state.viewerCount(),
                state.bidCounts().bidCount(),
                state.bidCounts().bidderCount(),
                RoomStateWinnerResponse.from(state.winnerName()),
                state.recentBids().stream().map(RoomStateBidResponse::from).toList());
    }
}
