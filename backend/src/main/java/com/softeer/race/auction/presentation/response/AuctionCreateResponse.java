package com.softeer.race.auction.presentation.response;

import com.softeer.race.auction.application.dto.AuctionCreateInfo;
import java.time.LocalDateTime;

public record AuctionCreateResponse(Long auctionId, Long vehicleId, long startPrice, LocalDateTime startAt,
                                    LocalDateTime roomOpenAt, LocalDateTime endAt, String status) {

    public static AuctionCreateResponse from(AuctionCreateInfo info) {
        return new AuctionCreateResponse(
                info.auctionId(), info.vehicleId(),
                info.startPrice(), info.startAt(),
                info.roomOpenAt(), info.endAt(),
                info.status());
    }
}
