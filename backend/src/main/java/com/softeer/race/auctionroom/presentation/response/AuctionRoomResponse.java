package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.AuctionRoomView;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.domain.RoomPhase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "경매방 현황")
public record AuctionRoomResponse(
        @Schema(description = "경매 식별자", example = "1")
        long auctionId,

        @Schema(description = "방 단계, 이 이름들이 API 계약이라 값을 그대로 비교해도 된다", example = "LIVE")
        RoomPhase phase,

        @Schema(description = "경매 차량")
        VehicleResponse vehicle,

        @Schema(description = "시작가", example = "10000000")
        long startPrice,

        @Schema(description = "현재가, 입찰이 없으면 시작가와 같다", example = "12500000")
        long currentPrice,

        @Schema(description = "방에 들어갈 수 있게 되는 시각, 입찰 시작 30분 전이다", example = "2026-08-03T20:00:00")
        LocalDateTime openAt,

        @Schema(description = "입찰이 시작되는 시각", example = "2026-08-03T20:30:00")
        LocalDateTime startAt,

        @Schema(description = "마감 시각, 연장되면 뒤로 밀린다", example = "2026-08-03T21:00:00")
        LocalDateTime endAt,

        @Schema(description = "응답을 만든 서버 시각(KST), 클라이언트 시계 보정에 쓴다",
                example = "2026-08-03T20:45:12")
        LocalDateTime serverTime,

        @Schema(description = "지금 방에 연결된 구독 수, 한 사람이 창을 둘 열면 둘로 센다", example = "12")
        int connectedCount,

        @Schema(description = "지금까지 입찰한 사람 수", example = "4")
        long bidderCount,

        @Schema(description = "지금까지 들어온 입찰 건수, 최근 호가 20건과 달리 전체를 센다", example = "37")
        long bidCount,

        @Schema(description = "낙찰자, 낙찰 확정 전에는 없다")
        WinnerResponse winner,

        @Schema(description = "조회한 사람이 이 차를 내놓은 사람인지")
        boolean sellerIsMine,

        @Schema(description = "최근 호가, 최신순 최대 20건")
        List<RecentBidResponse> recentBids
) {

    public static AuctionRoomResponse from(AuctionRoomView view) {
        RoomState state = view.state();

        return new AuctionRoomResponse(
                state.auctionId(),
                state.phase(),
                VehicleResponse.from(view.vehicle()),
                state.startPrice(),
                state.currentPrice(),
                state.openAt(),
                state.startAt(),
                state.endAt(),
                state.serverTime(),
                state.connectedCount(),
                state.bidCounts().bidderCount(),
                state.bidCounts().bidCount(),
                WinnerResponse.from(view),
                view.sellerIsMine(),
                view.recentBids().stream().map(RecentBidResponse::from).toList());
    }
}