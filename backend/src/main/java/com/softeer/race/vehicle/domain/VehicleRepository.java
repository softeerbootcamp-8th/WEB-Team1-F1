package com.softeer.race.vehicle.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * 평가 결과 변경과 경매 등록이 같은 차량에서 동시에 성립하지 않도록 차량 한 건을 잠근다.
     * <p>
     * join fetch를 붙이지 않는다. 판매자까지 함께 잠기면 서로 다른 차량을 등록하는 요청도 같은
     * 판매자 행을 두고 불필요하게 기다리게 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :vehicleId")
    Optional<Vehicle> findByIdForUpdate(@Param("vehicleId") long vehicleId);
}
