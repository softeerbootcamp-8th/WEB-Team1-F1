package com.softeer.race.auctionlist.domain;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;

import java.util.List;

/**
 * 목록에 거는 차량·가격 조건. 모든 필드가 null 허용이고 null은 조건 없음이다.
 * 제조사·변속기는 화면이 단일 선택이라 단수로 받고, 연료만 다중 선택이라 리스트다.
 */
public record AuctionListFilter(
        Manufacturer manufacturer,
        List<FuelType> fuelTypes,
        Transmission transmission,
        Integer mileageMin,
        Integer mileageMax,
        Integer modelYearMin,
        Integer modelYearMax,
        Long priceMin,
        Long priceMax
) {

    private static final AuctionListFilter NONE =
            new AuctionListFilter(null, null, null, null, null, null, null, null, null);

    public static AuctionListFilter none() {
        return NONE;
    }
}
