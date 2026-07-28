package com.softeer.race.auction.application.dto;

import com.softeer.race.auction.domain.Auction;

import java.time.LocalDateTime;

/**
 * 서비스 계층 반환값. 엔티티를 웹 계층에 노출하지 않기 위해 트랜잭션 안에서 변환한다
 */
public record AuctionCreateInfo(
        Long auctionId,
        Long vehicleId,
        long startPrice,
        LocalDateTime startAt,
        LocalDateTime roomOpenAt,
        LocalDateTime endAt,
        String status
) {

    public static AuctionCreateInfo from(Auction auction) {
        return new AuctionCreateInfo(
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
