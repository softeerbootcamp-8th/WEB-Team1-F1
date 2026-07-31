package com.softeer.race.sell.application.dto.info;

import com.softeer.race.auction.domain.Auction;

import java.time.LocalDateTime;

/**
 * 서비스 계층 반환값. 엔티티를 웹 계층에 노출하지 않기 위해 트랜잭션 안에서 변환한다.
 * <p>
 * 필드 구성은 {@code AuctionCreateInfo}와 같지만 그 타입을 재사용하지 않는다. sell의 응답 경로에
 * auction 패키지 타입을 끌어오면 두 유스케이스의 응답이 한 타입에 묶인다.
 */
public record SellApplicationInfo(
        Long auctionId,
        Long vehicleId,
        long startPrice,
        LocalDateTime startAt,
        LocalDateTime roomOpenAt,
        LocalDateTime endAt,
        String status
) {

    public static SellApplicationInfo from(Auction auction) {
        return new SellApplicationInfo(
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
