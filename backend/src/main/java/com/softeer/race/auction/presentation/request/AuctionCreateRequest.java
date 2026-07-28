package com.softeer.race.auction.presentation.request;

import com.softeer.race.auction.application.dto.AuctionCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AuctionCreateRequest(

        @Schema(description = "경매에 등록할 차량 ID", example = "1000")
        @NotNull Long vehicleId,

        @Schema(description = "시작가(원)", example = "10000000")
        @NotNull @PositiveOrZero Long startPrice,

        @Schema(description = "경매 시작 시각", example = "2026-07-28T12:00:00")
        @NotNull LocalDateTime startAt,

        @Schema(description = "경매글 제목", example = "그랜저 IG 하이브리드 익스클루시브 스페셜")
        @NotBlank
        @Size(max = 100)
        String title,

        @Schema(description = "차량 설명", example = "단순교환 무사고 비흡연 차량입니다.")
        @Size(max = 5000)
        String description
) {

    /**
     * 웹 계층 요청을 서비스 계층 입력값으로 변환한다
     */
    public AuctionCreateCommand toCommand() {
        return new AuctionCreateCommand(vehicleId, startPrice, startAt, title, description);
    }
}
