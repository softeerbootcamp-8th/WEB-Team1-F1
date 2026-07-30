package com.softeer.race.vehicle.infrastructure;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * 차량 제원 원장을 읽는 리포지토리.
 *
 * <p>카탈로그는 시드로만 채워지고 애플리케이션은 읽기만 한다.
 * JpaRepository 를 상속하면 save·deleteAll 이 딸려오므로 필요한 조회 하나로 좁혔다.
 */
public interface VehicleCatalogRepository extends Repository<VehicleCatalog, Long> {

    /** 번호판과 소유자명이 모두 맞는 한 건을 찾는다, 소유자명이 다르면 없는 것으로 취급된다 */
    // plate_number 가 유니크라 인덱스가 이미 한 행으로 좁혀준다, 복합 인덱스를 따로 두지 않는다
    Optional<VehicleCatalog> findByPlateNumberAndOwnerName(String plateNumber, String ownerName);
}