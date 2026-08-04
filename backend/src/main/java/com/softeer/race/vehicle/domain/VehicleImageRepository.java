package com.softeer.race.vehicle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    Optional<VehicleImage> findFirstByVehicleOrderBySortOrderAsc(Vehicle vehicle);

    List<VehicleImage> findAllByVehicleOrderBySortOrderAsc(Vehicle vehicle);

    /**
     * 사진을 다시 등록할 때 기존 것을 전부 지운다.
     * <p>
     * 벌크 delete가 아니라 조회 후 개별 삭제다. 한 차량의 이미지는 많아야 수십 건이라 그 비용이
     * 문제가 되지 않고, 벌크로 지우면 영속성 컨텍스트를 우회해 같은 트랜잭션에 남은 엔티티가
     * stale이 된다.
     */
    void deleteAllByVehicle(Vehicle vehicle);
}
