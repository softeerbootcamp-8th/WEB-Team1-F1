package com.softeer.race.deal.presentation.response;

import com.softeer.race.deal.application.dto.DealSliceInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "거래 목록 한 페이지")
public record DealSliceResponse(

        @Schema(description = "거래 목록, 최근에 만들어진 것부터")
        List<DealCardResponse> content,

        @Schema(description = "응답을 만든 서버 시각, 상대 시각 표시에 쓴다",
                example = "2026-08-09T12:00:00")
        LocalDateTime serverTime,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 요청에 그대로 보낼 커서, 마지막 페이지면 null", example = "7")
        Long nextCursor
) {

    public static DealSliceResponse from(DealSliceInfo info) {
        return new DealSliceResponse(
                info.content().stream().map(DealCardResponse::from).toList(),
                info.serverTime(),
                info.hasNext(),
                info.nextCursor());
    }
}
