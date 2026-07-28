package com.softeer.race.auction.application.dto;

import java.time.LocalDateTime;

/**
 * 서비스 계층 입력값. 웹 계층 요청 형식이 바뀌어도 서비스가 영향받지 않도록 분리한다
 */
public record AuctionCreateCommand(
        Long vehicleId,
        long startPrice,
        LocalDateTime startAt,
        String title,
        String description
) {
}
