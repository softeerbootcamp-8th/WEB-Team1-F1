package com.softeer.race.auctionlist.application.dto;

import com.softeer.race.auctionroom.domain.RoomPhase;

import java.time.LocalDateTime;

// 남은 시간을 내리지 않는다. 절대 시각과 서버 시각을 주고 카운트다운은 화면이 돌린다.
public record AuctionCardInfo(
        Long auctionId,
        RoomPhase phase,
        String thumbnailUrl,
        String model,
        Integer modelYear,
        Integer mileage,
        Long startPrice,
        Long currentPrice, // 입찰 전이면 시작가로 채워 내려간다.
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int connectedCount
) {
}
