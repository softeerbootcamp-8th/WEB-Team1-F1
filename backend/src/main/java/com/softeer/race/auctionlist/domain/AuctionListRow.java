package com.softeer.race.auctionlist.domain;

import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDateTime;

// 목록 카드 한 건
// 정렬 값은 담지 않는다. 어느 그룹에서 읽혔는지에 따라 달라지므로 AuctionListGroup 이 계산한다.
public record AuctionListRow(
        Long auctionId,
        Long vehicleId,
        String thumbnailUrl,
        String manufacturer,
        String model,
        Integer modelYear,
        Integer mileage,
        Long startPrice,
        Long currentPrice,
        LocalDateTime roomOpenAt,
        LocalDateTime startTime,
        LocalDateTime currentEndTime
) {

    /**
     * 저장 문자열을 제조사 enum 으로. 응답까지 문자열로 흘리면 오타가 계약 밖 값으로 새어 나간다.
     */
    public Manufacturer manufacturerType() {
        return Manufacturer.valueOf(manufacturer);
    }

    /**
     * 화면에 보일 현재가, 입찰이 없으면 시작가
     */
    // 경매방 상세도 같은 규칙을 자기 안에서 푼다. 두 화면이 같은 값을 보이는지는 통합테스트가 지킨다.
    public long displayPrice() {
        return currentPrice != null ? currentPrice : startPrice;
    }
}
