package com.softeer.race.auctionlist.presentation.response;

import com.softeer.race.auctionlist.application.dto.AuctionListCursor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "다음 페이지 요청에 그대로 돌려보낼 값")
public record AuctionCursorResponse(

        @Schema(description = "정렬 기준 시각, 첫 페이지의 조회 시각을 끝까지 유지한다",
                example = "2026-08-03T12:00:00")
        LocalDateTime snapshotAt,

        @Schema(description = "그룹 순번, 진행중 1 예정 2 종료 3", example = "1")
        int sortPriority,

        @Schema(description = "그룹 안에서의 정렬 시각. 예정은 시작 시각, 나머지는 마감 시각이다",
                example = "2026-08-03T12:10:00")
        LocalDateTime sortAt,

        @Schema(description = "정렬 시각이 같을 때를 가르는 값", example = "1")
        long auctionId
) {

    public static AuctionCursorResponse from(AuctionListCursor cursor) {
        return new AuctionCursorResponse(
                cursor.snapshotAt(),
                cursor.group().order(),
                cursor.sortAt(),
                cursor.auctionId());
    }
}