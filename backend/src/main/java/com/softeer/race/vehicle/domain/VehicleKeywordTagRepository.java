package com.softeer.race.vehicle.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VehicleKeywordTagRepository extends JpaRepository<VehicleKeywordTag, Long> {

    /**
     * 이 차량에 매겨진 키워드들. 정렬하지 않는다 — {@code order by keyword}는 이름 문자열 순서라
     * {@link VehicleKeyword}의 선언 순서와 어긋나므로, 정렬은 읽은 뒤 자바에서 한다.
     */
    List<VehicleKeywordTag> findAllByVehicle(Vehicle vehicle);

    /**
     * 재제출 때 앞서 매긴 키워드를 전부 지운다.
     * <p>
     * {@code VehicleImageRepository.deleteAllByVehicle}과 같이 벌크가 아닌 조회 후 개별 삭제다.
     * 한 차량의 키워드는 많아야 열 건 남짓이라 비용이 문제가 되지 않고, 벌크로 지우면 영속성
     * 컨텍스트를 우회해 같은 트랜잭션에 남은 엔티티가 stale 이 된다.
     */
    void deleteAllByVehicle(Vehicle vehicle);

    @Query("""
            select new com.softeer.race.vehicle.domain.VehicleKeywordRow(t.vehicle.id, t.keyword)
            from VehicleKeywordTag t
            where t.vehicle.id in :vehicleIds
            """)
    List<VehicleKeywordRow> findRowsByVehicleIdIn(@Param("vehicleIds") Collection<Long> vehicleIds);
}
