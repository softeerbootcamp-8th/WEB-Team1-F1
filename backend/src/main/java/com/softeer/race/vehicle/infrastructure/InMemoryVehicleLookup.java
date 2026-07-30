package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 데모용 하드코딩 카탈로그. 실제 제원 조회처가 붙기 전까지 판매 신청이 동작하게 하는 임시 구현이다.
 * <p>
 * 데이터를 별도 픽스처 클래스나 yml로 빼지 않고 이 클래스 안에 둔다. 밖으로 빼면 DB 구현체를 넣은 뒤에도
 * 어디선가 참조될 수 있는 죽은 데이터가 남는다. 클래스째로 지울 수 있는 형태를 유지한다.
 * <p>
 * TODO DB 구현체를 넣을 때 이 클래스를 삭제한다. 두 구현체가 동시에 {@code @Component}면
 * {@code NoUniqueBeanDefinitionException}으로 기동이 실패하므로 지우는 것을 잊을 수 없다.
 * (그래서 {@code @Profile}이나 {@code @ConditionalOnMissingBean}을 새로 들이지 않는다.
 * 조용히 잘못된 구현이 선택되느니 기동이 실패하는 편이 낫다.)
 */
@Component
public class InMemoryVehicleLookup implements VehicleLookup {

    // 번호판이 겹치면 toUnmodifiableMap이 클래스 로딩 시점에 IllegalStateException으로 잡아 준다
    private static final Map<String, VehicleSpec> CATALOG = Stream.of(
                    // 1번은 기존 픽스처(auction-create-fixture.sql)의 차량과 같은 값이다
                    new VehicleSpec("12가3456", "김민수",
                            Manufacturer.HYUNDAI, "그랜저 IG", 2021, 45_000,
                            FuelType.GASOLINE, Transmission.AUTOMATIC,
                            24_800_000L, "https://cdn.race.dev/vehicles/grandeur-ig.jpg"),
                    new VehicleSpec("34나5678", "이서연",
                            Manufacturer.KIA, "쏘렌토", 2022, 32_000,
                            FuelType.DIESEL, Transmission.AUTOMATIC,
                            31_500_000L, "https://cdn.race.dev/vehicles/sorento.jpg"),
                    new VehicleSpec("56다7890", "박지훈",
                            Manufacturer.GENESIS, "G80", 2023, 18_000,
                            FuelType.GASOLINE, Transmission.AUTOMATIC,
                            52_000_000L, "https://cdn.race.dev/vehicles/g80.jpg"),
                    new VehicleSpec("78라1234", "최유진",
                            Manufacturer.TESLA, "모델 3", 2022, 27_000,
                            FuelType.ELECTRIC, Transmission.AUTOMATIC,
                            38_900_000L, "https://cdn.race.dev/vehicles/model-3.jpg"),
                    // 이미지가 없는 차량을 일부러 하나 둔다. 썸네일 없는 경로가 데모 중에 실제로 실행돼야
                    // 목록 카드의 이미지 없음 처리를 그 자리에서 확인할 수 있다
                    new VehicleSpec("90마5678", "정하늘",
                            Manufacturer.BMW, "520i", 2020, 61_000,
                            FuelType.GASOLINE, Transmission.AUTOMATIC,
                            29_400_000L, null))
            .collect(Collectors.toUnmodifiableMap(VehicleSpec::plateNumber, Function.identity()));

    /**
     * 번호판을 정규화하지 않는다. 공백·대시가 섞인 입력은 여기서 손보는 대신
     * {@code SellApplicationRequest}의 {@code @Pattern}이 애초에 막는다.
     * <p>
     * 정규화를 구현체에 넣으면 나중 DB 구현체가 같은 규칙을 다시 구현해야 하고, 두 규칙이 어긋나는 순간
     * "조회는 되는데 저장된 번호판은 다른" 상태가 된다.
     */
    @Override
    public Optional<VehicleSpec> findByPlateNumber(String plateNumber) {
        return Optional.ofNullable(CATALOG.get(plateNumber));
    }
}
