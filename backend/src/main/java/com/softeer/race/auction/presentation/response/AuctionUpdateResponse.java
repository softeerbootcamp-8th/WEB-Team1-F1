package com.softeer.race.auction.presentation.response;

import com.softeer.race.auction.application.dto.AuctionUpdateInfo;

import java.time.LocalDateTime;

public record AuctionUpdateResponse(Long auctionId, Long vehicleId, long startPrice, LocalDateTime startAt,
                                    LocalDateTime roomOpenAt, LocalDateTime endAt, String status) {

    public static AuctionUpdateResponse from(AuctionUpdateInfo info) {
        return new AuctionUpdateResponse(
                info.auctionId(), info.vehicleId(),
                info.startPrice(), info.startAt(),
                info.roomOpenAt(), info.endAt(),
                info.status());
    }
}
