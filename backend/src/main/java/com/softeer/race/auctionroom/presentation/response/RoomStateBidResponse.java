package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.application.RoomStateBid;
import com.softeer.race.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "호가 한 건, 보는 사람이 정해지지 않아 내 입찰 표시가 없다, 그 표시가 필요하면 조회 응답의 호가를 쓴다")
public record RoomStateBidResponse(
        @Schema(description = "가운데를 마스킹한 입찰자 이름", example = "김*현")
        String name,

        @Schema(description = "입찰자 역할, DEALER 아니면 GENERAL 이다", example = "DEALER")
        Role role,

        @Schema(description = "입찰 금액", example = "12500000")
        long amount,

        @Schema(description = "입찰 시각", example = "2026-08-03T20:44:31")
        LocalDateTime bidAt
) {

    static RoomStateBidResponse from(RoomStateBid bid) {
        return new RoomStateBidResponse(bid.bidderName().value(), bid.role(), bid.amount(), bid.bidAt());
    }
}
