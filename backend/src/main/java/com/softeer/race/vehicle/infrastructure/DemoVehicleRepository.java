package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 아직 아무도 쓰지 않은 데모 차량을 앞에서부터 뽑는다.
 *
 * <p>{@code VehicleCatalogRepository} 와 나눠 둔다. 그쪽은 번호판 한 건을 찾는 원장 조회이고
 * 이쪽은 평가 진행 여부까지 보는 목록 조회라, 한 인터페이스에 두면 원장이 평가를 알게 된다.
 *
 * <p>{@code Evaluation} 을 참조해 vehicle 패키지가 evaluation 을 알게 된다. 진행 중 판정을
 * 신청 쪽과 한 벌로 유지하려면 그 상태 집합을 그대로 읽는 수밖에 없어 받아들인 결합이다.
 */
public interface DemoVehicleRepository extends Repository<VehicleCatalog, Long> {

    /**
     * 진행 중인 평가가 걸리지 않은 차량을 id 순으로 최대 {@code limit} 대.
     * <p>
     * 상한을 DB 에 건다. 애플리케이션에서 자르면 카탈로그 전건이 메모리로 올라오고,
     * 자르기 전에 걸면 걸러진 만큼 응답이 10대보다 적어진다.
     * <p>
     * id 오름차순이라 목록이 "아직 안 쓴 가장 오래된 10대"가 된다. 앞의 차가 신청되면
     * 그 자리를 다음 후보가 채우는 소진 큐가 되고, order by 를 빼면 자를 10대가 매번 달라진다.
     * <p>
     * 번호판으로 잇는다. vehicle 행이 신청마다 새로 생겨 id 로는 이을 수 없고,
     * 이 조건이 {@code existsByVehiclePlateNumberAndStatusIn} 과 같은 기준이다.
     */
    @Query("""
            select new com.softeer.race.vehicle.infrastructure.DemoVehicleRow(
                c.plateNumber, c.ownerName, c.manufacturer, c.model, c.modelYear)
            from VehicleCatalog c
            where not exists (
                select 1
                from Evaluation e
                where e.vehicle.plateNumber = c.plateNumber
                    and e.status in :inProgressStatuses)
            order by c.id
            """)
    List<DemoVehicleRow> findAvailable(
            @Param("inProgressStatuses") Collection<EvaluationStatus> inProgressStatuses,
            Limit limit);
}