package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.Manufacturer;

/**
 * 데모 차량 한 대. 카탈로그 행에서 도움말에 필요한 다섯 칸만 뽑아 온다.
 * <p>
 * {@code VehicleCatalog} 엔티티를 그대로 내보내지 않는다. basePrice 가 딸려 올라오면
 * 응답에 실릴 여지가 남고, 그 값은 예상 시세와 나란히 놓이는 순간 감가율이 역산된다.
 */
public record DemoVehicleRow(
        String plateNumber,
        String ownerName,
        Manufacturer manufacturer,
        String model,
        int modelYear
) {
}