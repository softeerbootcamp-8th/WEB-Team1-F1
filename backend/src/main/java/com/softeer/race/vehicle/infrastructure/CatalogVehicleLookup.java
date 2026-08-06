package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 자체 보유 카탈로그 테이블을 조회하는 구현체. 외부 차량정보 API가 붙기 전까지 그 자리를 대신한다.
 *
 * <p>소유자명 대조를 쿼리 조건으로 넣어, 호출자에게 "행은 찾았지만 소유자가 다르다"는 상태가 아예
 * 도달하지 않게 한다. 그 상태가 존재하면 언젠가 미등록과 불일치가 다른 메시지로 갈라지고,
 * 번호판 대입으로 소유자명이 새어나간다.
 *
 * <p>번호판을 정규화하지 않는다. 여기서 손보면 나중 외부 API 구현체가 같은 규칙을 다시 구현해야 하고,
 * 두 규칙이 어긋나면 "조회는 되는데 저장된 번호판은 다른" 상태가 된다. 공백은 요청 검증이 막는다.
 */
@Component
@RequiredArgsConstructor
public class CatalogVehicleLookup implements VehicleLookup {

    private final VehicleCatalogRepository vehicleCatalogRepository;

    @Override
    public Optional<VehicleSpec> find(String plateNumber, String ownerName) {
        return vehicleCatalogRepository.findByPlateNumberAndOwnerName(plateNumber, ownerName)
                .map(VehicleCatalog::toSpec);
    }
}
