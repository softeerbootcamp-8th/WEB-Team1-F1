package com.softeer.race.auctionlist.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경매 하나의 시청자 수 변화")
public record AudienceResponse(

        @Schema(description = "경매 식별자", example = "1")
        Long auctionId,

        @Schema(description = "그 경매방을 보고 있는 사람 수", example = "3")
        Integer connectedCount
) {
}