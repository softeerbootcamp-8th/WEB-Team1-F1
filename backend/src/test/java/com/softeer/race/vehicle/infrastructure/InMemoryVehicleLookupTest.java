package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("하드코딩 차량 조회")
class InMemoryVehicleLookupTest {

    private final InMemoryVehicleLookup lookup = new InMemoryVehicleLookup();

    // 필드 하나만 비교하면 modelYear와 mileage처럼 같은 타입끼리 뒤바뀐 것을 못 잡는다
    @Test
    @DisplayName("등록된 번호판은 제원 전체를 그대로 돌려준다")
    void findsRegisteredPlate() {
        assertThat(lookup.findByPlateNumber("12가3456"))
                .contains(new VehicleSpec("12가3456", "김민수",
                        Manufacturer.HYUNDAI, "그랜저 IG", 2021, 45_000,
                        FuelType.GASOLINE, Transmission.AUTOMATIC,
                        24_800_000L, "https://cdn.race.dev/vehicles/grandeur-ig.jpg"));
    }

    @Test
    @DisplayName("등록되지 않은 번호판은 비어 있다")
    void missingPlateIsEmpty() {
        assertThat(lookup.findByPlateNumber("99하9999")).isEmpty();
    }

    // 정규화를 구현체에 넣지 않기로 한 결정을 고정한다
    // 공백·대시는 요청 단계의 @Pattern이 막고, 여기까지 오면 그냥 미등록이다
    @ParameterizedTest
    @ValueSource(strings = {"12가 3456", "12-가-3456", "12가3456 ", ""})
    @DisplayName("공백이나 대시가 섞인 번호판은 정규화하지 않고 미등록으로 본다")
    void doesNotNormalizePlate(String plateNumber) {
        assertThat(lookup.findByPlateNumber(plateNumber)).isEmpty();
    }

    // 썸네일 없는 경로가 데모에서 실제로 실행되려면 카탈로그에 이미지 없는 차가 있어야 한다
    @Test
    @DisplayName("대표 이미지가 없는 차량이 카탈로그에 존재한다")
    void hasVehicleWithoutImage() {
        assertThat(lookup.findByPlateNumber("90마5678"))
                .get()
                .extracting(VehicleSpec::mainImageUrl)
                .isNull();
    }

    @Test
    @DisplayName("기준가는 항상 양수라 시작가로 바로 쓸 수 있다")
    void basePriceIsPositive() {
        assertThat(lookup.findByPlateNumber("56다7890"))
                .get()
                .extracting(VehicleSpec::basePrice)
                .isEqualTo(52_000_000L);
    }
}
