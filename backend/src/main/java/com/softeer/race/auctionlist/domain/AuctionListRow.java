package com.softeer.race.auctionlist.domain;

import java.time.LocalDateTime;

// 목록 카드 한 건
// 정렬 값은 담지 않는다. 어느 그룹에서 읽혔는지에 따라 달라지므로 AuctionListGroup 이 계산한다.
public record AuctionListRow(
        Long auctionId,
        String thumbnailUrl,
        String model,
        Integer modelYear,
        Integer mileage,
        Long startPrice,
        Long currentPrice,
        LocalDateTime roomOpenAt,
        LocalDateTime startTime,
        LocalDateTime currentEndTime
) {
}
