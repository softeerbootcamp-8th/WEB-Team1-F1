package com.softeer.race.evaluation.domain;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    /**
     * 같은 차량으로 진행 중인 신청이 있는지. 판정 기준이 번호판 문자열인 이유는 vehicle 행이
     * 신청마다 새로 생기기 때문이다 — {@code Vehicle.plateNumber}에 unique 제약이 없고, 같은 차를
     * 반복 출품할 수 있어야 해서 앞으로도 붙일 수 없다. vehicle_id로 묶으면 방금 만든 차량만
     * 보게 되어 중복이 전부 통과한다.
     * <p>
     * 신청자를 조건에 넣지 않는다. 같은 차를 두 사람이 동시에 신청하는 것도 막아야 하고,
     * 평가사가 한 차량에 두 번 방문하는 일이 없어야 한다.
     */
    boolean existsByVehiclePlateNumberAndStatusIn(String plateNumber,
                                                  Collection<EvaluationStatus> statuses);
}
