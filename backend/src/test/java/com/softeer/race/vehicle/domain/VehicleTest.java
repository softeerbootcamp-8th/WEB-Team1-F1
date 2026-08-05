package com.softeer.race.vehicle.domain;

import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("차량 생성")
class VehicleTest {

    /**
     * "제원은 클라이언트가 아니라 서버가 채운다"의 유일한 실행 가능한 보증선이다.
     * 어느 필드 하나라도 조회 결과와 달라지면 위조 방지가 무너진다.
     */
    @Test
    @DisplayName("조회된 제원이 차량에 그대로 옮겨지고 넘겨받은 값이 추정가가 된다")
    void createCopiesSpec() {
        // given
        User seller = mock(User.class);
        VehicleSpec spec = new VehicleSpec("12가3456", "김민수",
                Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                34_000_000L, "https://cdn.race.dev/vehicles/grandeur-ig.jpg");

        // when
        Vehicle vehicle = Vehicle.create(seller, spec, 45_000, 23_200_000L);

        // then
        assertThat(vehicle.getSeller()).isSameAs(seller);
        assertThat(vehicle.getPlateNumber()).isEqualTo("12가3456");
        assertThat(vehicle.getManufacturer()).isEqualTo(Manufacturer.HYUNDAI);
        assertThat(vehicle.getModel()).isEqualTo("그랜저 IG");
        // 둘 다 int라 서로 뒤바뀌어도 컴파일은 통과한다, 값으로 고정한다
        assertThat(vehicle.getModelYear()).isEqualTo(2021);
        assertThat(vehicle.getMileage()).isEqualTo(45_000);
        assertThat(vehicle.getFuelType()).isEqualTo(FuelType.GASOLINE);
        assertThat(vehicle.getTransmission()).isEqualTo(Transmission.AUTOMATIC);
        // 기준가(3400만)가 아니라 호출자가 산정해 넘긴 예상 시세다.
        // spec.basePrice()를 그대로 넣는 구현으로 되돌아가면 여기가 깨진다
        assertThat(vehicle.getEstimatedPrice()).isEqualTo(23_200_000L);
    }

    // 차량은 이미지 URL을 갖지 않는다, 대표 이미지는 VehicleImage에만 남는다
    @Test
    @DisplayName("대표 이미지가 없는 제원도 차량으로 만들 수 있다")
    void createWithoutImage() {
        VehicleSpec spec = new VehicleSpec("90마5678", "정하늘",
                Manufacturer.BMW, "520i", 2020,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                68_000_000L, null);

        Vehicle vehicle = Vehicle.create(mock(User.class), spec, 61_000, 41_370_000L);

        assertThat(vehicle.getPlateNumber()).isEqualTo("90마5678");
        assertThat(vehicle.getEstimatedPrice()).isEqualTo(41_370_000L);
    }

    /**
     * "경매가 붙은 차량은 주행거리가 채워져 있다"는 불변식이 이 메서드 하나에 달려 있다.
     * 방문견적으로 만들어진 차량이 값을 갖는 유일한 경로다.
     */
    @Test
    @DisplayName("진단을 마치면 비어 있던 주행거리와 예상 시세가 채워진다")
    void completeDiagnosis() {
        // given : 방문견적 신청이 만드는 상태다
        Vehicle vehicle = Vehicle.pendingDiagnosis(mock(User.class), pendingSpec());
        assertThat(vehicle.getMileage()).isNull();
        assertThat(vehicle.getEstimatedPrice()).isNull();

        // when
        vehicle.completeDiagnosis(45_000, 21_500_000L);

        // then
        assertThat(vehicle.getMileage()).isEqualTo(45_000);
        assertThat(vehicle.getEstimatedPrice()).isEqualTo(21_500_000L);
    }

    // 평가사가 잘못 적은 값을 고치려면 결과를 다시 제출해야 하고, 그 재제출이 여기로 온다
    @Test
    @DisplayName("이미 진단된 차량도 다시 제출한 값으로 덮인다")
    void completeDiagnosisOverwrites() {
        Vehicle vehicle = Vehicle.pendingDiagnosis(mock(User.class), pendingSpec());
        vehicle.completeDiagnosis(45_000, 21_500_000L);

        vehicle.completeDiagnosis(54_000, 20_800_000L);

        assertThat(vehicle.getMileage()).isEqualTo(54_000);
        assertThat(vehicle.getEstimatedPrice()).isEqualTo(20_800_000L);
    }

    private static VehicleSpec pendingSpec() {
        return new VehicleSpec("12가3456", "김민수",
                Manufacturer.HYUNDAI, "아반떼 CN7", 2022,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                34_000_000L, null);
    }
}
