package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomResultView;
import com.softeer.race.auctionroom.domain.AuctionOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "끝난 경매의 결과 요약, 더 이상 바뀌지 않는다")
public record RoomResultResponse(
        @Schema(description = "경매 식별자", example = "1")
        long auctionId,

        @Schema(description = "경매 결과, 입찰이 한 건도 없었으면 유찰이다. "
                + "SOLD 면 낙찰가와 낙찰자가 반드시 채워지고 UNSOLD 면 둘 다 반드시 null 이다",
                example = "SOLD")
        AuctionOutcome outcome,

        @Schema(description = "경매 차량")
        VehicleResponse vehicle,

        @Schema(description = "시작가", example = "10000000")
        long startPrice,

        @Schema(description = "최종 낙찰가, 유찰이면 키는 있고 값이 null 이다", example = "24000000")
        Long winningPrice,

        @Schema(description = "낙찰자, 유찰이면 키는 있고 값이 null 이다")
        WinnerResponse winner,

        @Schema(description = "조회한 사람이 이 차를 내놓은 사람인지")
        boolean sellerIsMine,

        @Schema(description = "조회한 사람의 성적, 입찰한 적이 없으면 키는 있고 값이 null 이다")
        MyStandingResponse myStanding,

        @Schema(description = "끝날 때까지 들어온 입찰 건수", example = "4")
        long bidCount,

        @Schema(description = "끝날 때까지 입찰한 사람 수, 한 사람이 여러 번 넣으면 건수만 늘어난다", example = "2")
        long bidderCount,

        @Schema(description = "마감 임박 입찰로 마감이 밀린 횟수", example = "1")
        int extensionCount,

        @Schema(description = "입찰이 시작된 시각", example = "2026-08-03T18:30:00")
        LocalDateTime startAt,

        @Schema(description = "최종 마감 시각, 연장된 만큼 뒤로 밀려 있다", example = "2026-08-03T18:50:10")
        LocalDateTime endAt,

        @Schema(description = "결과를 볼 수 있는 구간이 끝나는 시각, 마감 뒤 5분이다",
                example = "2026-08-03T18:55:10")
        LocalDateTime resultEndAt,

        @Schema(description = "응답을 만든 서버 시각(KST), 남은 열람 시간을 세는 기준이다",
                example = "2026-08-03T18:51:00")
        LocalDateTime serverTime,

        @Schema(description = "가격이 오른 과정, 시간순 전체이고 유찰이면 빈 배열이다. "
                + "시작가는 담기지 않으므로 곡선의 첫 점은 첫 입찰이다")
        List<PricePointResponse> priceCurve
) {

    public static RoomResultResponse from(RoomResultView view) {
        return new RoomResultResponse(
                view.auctionId(),
                view.outcome(),
                VehicleResponse.from(view.vehicle()),
                view.startPrice(),
                view.winningPrice(),
                WinnerResponse.from(view),
                view.sellerIsMine(),
                MyStandingResponse.from(view),
                view.bidCounts().bidCount(),
                view.bidCounts().bidderCount(),
                view.extensionCount(),
                view.startAt(),
                view.endAt(),
                view.resultViewingEndsAt(),
                view.serverTime(),
                view.priceCurve().stream().map(PricePointResponse::from).toList()
        );
    }
}
