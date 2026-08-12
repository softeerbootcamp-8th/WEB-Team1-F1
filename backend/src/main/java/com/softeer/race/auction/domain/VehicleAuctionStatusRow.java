package com.softeer.race.auction.domain;

/** 평가 목록의 여러 차량에 최신 경매 상태를 한 번에 붙이기 위한 조회 행 */
public record VehicleAuctionStatusRow(
        Long vehicleId,
        AuctionStatus status
) {
}
