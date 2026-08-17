package com.softeer.race.auctionlist.presentation.request;

import com.softeer.race.auctionlist.domain.AuctionListFilter;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 목록에 걸 차량·가격 조건. 전부 선택 파라미터고 없으면 조건 없음이다.
 * 필터를 바꾸면 커서 없이 첫 페이지부터 다시 요청하는 것은 클라이언트 책임이다.
 */
public record AuctionListFilterRequest(
        Manufacturer manufacturer,
        List<FuelType> fuelTypes,
        Transmission transmission,
        @PositiveOrZero Integer mileageMin,
        @PositiveOrZero Integer mileageMax,
        Integer modelYearMin,
        Integer modelYearMax,
        @PositiveOrZero
        @Max(value = 1_000_000_000_000L, message = "검색 금액은 1조원을 넘을 수 없습니다.")
        Long priceMin,

        @PositiveOrZero
        @Max(value = 1_000_000_000_000L, message = "검색 금액은 1조원을 넘을 수 없습니다.")
        Long priceMax
) {

    // 뒤집힌 범위는 항상 빈 결과라 실수일 수밖에 없다. 조용히 빈 목록을 주면 원인을 찾기 어렵다.
    @AssertTrue(message = "주행거리 범위는 최소가 최대보다 클 수 없습니다.")
    boolean isMileageRangeValid() {
        return mileageMin == null || mileageMax == null || mileageMin <= mileageMax;
    }

    @AssertTrue(message = "연식 범위는 최소가 최대보다 클 수 없습니다.")
    boolean isModelYearRangeValid() {
        return modelYearMin == null || modelYearMax == null || modelYearMin <= modelYearMax;
    }

    @AssertTrue(message = "가격 범위는 최소가 최대보다 클 수 없습니다.")
    boolean isPriceRangeValid() {
        return priceMin == null || priceMax == null || priceMin <= priceMax;
    }

    public AuctionListFilter toFilter() {
        return new AuctionListFilter(manufacturer, fuelTypes, transmission,
                mileageMin, mileageMax, modelYearMin, modelYearMax, priceMin, priceMax);
    }
}
