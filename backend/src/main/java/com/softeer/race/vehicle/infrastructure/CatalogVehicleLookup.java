package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleSpec;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 자체 보유 카탈로그 테이블을 뒤지는 차량 조회기 구현체. 외부 API를 붙이기 전까지 그 자리를 대신한다.
 *
 * <p>소유자명 대조를 쿼리 조건으로 넣어, 호출자에게 "행은 찾았지만 소유자가 다르다"는 상태가
 * 아예 도달하지 않게 한다. 그 상태가 존재하면 언젠가 미등록과 불일치가 다른 메시지로 갈라지고,
 * 번호판 대입으로 소유자명이 새어나간다.
 */
@Component
@RequiredArgsConstructor
public class CatalogVehicleLookup implements VehicleLookup {

    private final VehicleCatalogRepository vehicleCatalogRepository;

    @Override
    public Optional<VehicleSpec> find(String plateNumber, String ownerName) {
        // 입력의 앞뒤 공백을 다듬는다. 앞 공백은 어느 콜레이션에서도 불일치를 만들고,
        // 뒤 공백은 콜레이션에 따라 갈린다 — 개발용 DB(utf8mb4_unicode_ci)는 무시하지만
        // 통합테스트 컨테이너(MySQL 8.4 기본 utf8mb4_0900_ai_ci)는 다른 값으로 본다.
        // 환경에 따라 결과가 달라지지 않게 입력에서 정리한다.
        return vehicleCatalogRepository
                .findByPlateNumberAndOwnerName(plateNumber.trim(), ownerName.trim())
                .map(VehicleCatalog::toSpec);
    }
}