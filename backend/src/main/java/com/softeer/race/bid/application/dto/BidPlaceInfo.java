package com.softeer.race.bid.application.dto;

import java.time.LocalDateTime;

/**
 * 서비스 계층 반환값. 엔티티를 웹 계층에 노출하지 않기 위해 트랜잭션 안에서 변환한다.
 */
public record BidPlaceInfo(
        Long bidId,
        long amount,
        LocalDateTime endAt,
        LocalDateTime serverTime
) {
}
