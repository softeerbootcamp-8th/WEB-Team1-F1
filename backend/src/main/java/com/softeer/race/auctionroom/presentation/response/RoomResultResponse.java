package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomResultView;
import com.softeer.race.auctionroom.domain.AuctionOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

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

        @Schema(description = "대표 사진, 등록되지 않았으면 없다",
                example = "https://cdn.race.dev/avante-1.jpg")
        String thumbnailUrl,

        @Schema(description = "시작가", example = "10000000")
        long startPrice,

        @Schema(description = "최종 낙찰가, 유찰이면 키는 있고 값이 null 이다", example = "24000000")
        Long winningPrice,

        @Schema(description = "낙찰자, 유찰이면 키는 있고 값이 null 이다")
        WinnerResponse winner,

        @Schema(description = "끝날 때까지 들어온 입찰 건수", example = "4")
        long bidCount
) {

    public static RoomResultResponse from(RoomResultView view) {
        return new RoomResultResponse(
                view.auctionId(),
                view.outcome(),
                VehicleResponse.from(view.vehicle()),
                view.thumbnailUrl(),
                view.startPrice(),
                view.winningPrice(),
                view.winnerName() == null ? null : new WinnerResponse(view.winnerName().value(), view.winnerIsMine()),
                view.bidCount());
    }
}
