package com.softeer.race.vehicle.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("차량 조회 서비스")
class VehicleLookupServiceTest {

    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String IMAGE_URL = "https://cdn.race.dev/vehicles/grandeur-ig.jpg";

    /** 그 모델의 기준가. 응답으로 나가면 안 되는 값이다 */
    private static final long BASE_PRICE = 34_000_000L;

    @Mock
    private VehicleLookup vehicleLookup;

    private VehicleLookupService service;

    @BeforeEach
    void before() {
        service = new VehicleLookupService(vehicleLookup);
    }

    @Test
    @DisplayName("번호판과 소유자명이 맞으면 제원을 그대로 옮겨 준다")
    void lookup() {
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.of(spec(IMAGE_URL)));

        VehicleLookupInfo info = service.lookup(command());

        assertThat(info.plateNumber()).isEqualTo(PLATE_NUMBER);
        assertThat(info.manufacturer()).isEqualTo(Manufacturer.HYUNDAI);
        assertThat(info.model()).isEqualTo("그랜저 IG");
        assertThat(info.modelYear()).isEqualTo(2021);
        assertThat(info.fuelType()).isEqualTo(FuelType.GASOLINE);
        assertThat(info.transmission()).isEqualTo(Transmission.AUTOMATIC);
        assertThat(info.mainImageUrl()).isEqualTo(IMAGE_URL);
    }

    /**
     * 기준가와 소유자명이 Info 로 새지 않는다는 것을 record 컴포넌트 목록으로 고정한다.
     * <p>
     * 필드를 하나씩 단언하는 것으로는 "없다"를 증명할 수 없다 — 나중에 basePrice 를 추가해도
     * 기존 단언은 그대로 통과한다. 컴포넌트 이름 집합을 비교하면 늘어나는 순간 깨진다.
     */
    @Test
    @DisplayName("기준가와 소유자명은 결과에 담기지 않는다")
    void lookupDoesNotExposeBasePriceOrOwnerName() {
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.of(spec(IMAGE_URL)));

        service.lookup(command());

        assertThat(VehicleLookupInfo.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("plateNumber", "manufacturer", "model", "modelYear",
                        "fuelType", "transmission", "mainImageUrl")
                // 예상 시세도 없다. 주행거리를 모르는 상태의 금액은 아무것도 보증하지 않는다
                .doesNotContain("basePrice", "ownerName", "estimatedPrice", "mileage");
        assertThat(BASE_PRICE).isPositive();  // 조회는 기준가를 받지만 쓰지 않는다는 것을 남긴다
    }

    @Test
    @DisplayName("대표 이미지가 없는 차량도 조회된다")
    void lookupWithoutImage() {
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.of(spec(null)));

        assertThat(service.lookup(command()).mainImageUrl()).isNull();
    }

    @Test
    @DisplayName("미등록이든 소유자명 불일치든 같은 404 코드로 거부한다")
    void lookupNotFound() {
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(VehicleErrorCode.SPEC_NOT_FOUND));
    }

    // 소유자명을 빼고 조회하는 경로를 막는 테스트가 여기 있었다. 지금은 VehicleLookup 에
    // 번호판만 받는 메서드가 없어 컴파일러가 대신 막아 준다 — 테스트보다 강한 보증이다

    private static VehicleLookupCommand command() {
        return new VehicleLookupCommand(PLATE_NUMBER, OWNER_NAME);
    }

    private static VehicleSpec spec(String mainImageUrl) {
        return new VehicleSpec(PLATE_NUMBER, OWNER_NAME,
                Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                BASE_PRICE, mainImageUrl);
    }
}
