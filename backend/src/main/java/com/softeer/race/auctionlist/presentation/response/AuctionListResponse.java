package com.softeer.race.auctionlist.presentation.response;

import com.softeer.race.auctionlist.application.dto.AuctionListInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "경매글 목록 한 페이지")
public record AuctionListResponse(

        @Schema(description = "카드 목록, 진행중 → 예정 → 종료 순")
        List<AuctionCardResponse> content,

        @Schema(description = "응답을 만든 서버 시각, 클라이언트 시계 보정에 쓴다",
                example = "2026-08-03T12:00:00")
        LocalDateTime serverTime,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 커서, 마지막 페이지면 null")
        AuctionCursorResponse nextCursor
) {

    public static AuctionListResponse from(AuctionListInfo info) {
        return new AuctionListResponse(
                info.content().stream().map(AuctionCardResponse::from).toList(),
                info.serverTime(),
                info.hasNext(),
                info.nextCursor() != null ? AuctionCursorResponse.from(info.nextCursor()) : null);
    }
}