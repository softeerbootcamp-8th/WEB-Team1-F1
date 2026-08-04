package com.softeer.race.auction.application.dto;

import com.softeer.race.auction.domain.Auction;

import java.time.LocalDateTime;

public record AuctionUpdateInfo(
        Long auctionId,
        Long vehicleId,
        long startPrice,
        LocalDateTime startAt,
        LocalDateTime roomOpenAt,
        LocalDateTime endAt,
        String status
) {

    public static AuctionUpdateInfo from(Auction auction) {
        return new AuctionUpdateInfo(
                auction.getId(),
                auction.getPost().getVehicle().getId(),
                auction.getStartPrice(),
                auction.getStartTime(),
                auction.getRoomOpenAt(),
                auction.getCurrentEndTime(),
                auction.getStatus().name()
        );
    }
}


