package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import java.time.LocalDate;

/**
 * 배정 확정 결과. 담당이 된 평가사에게 방문에 필요한 것을 전달한다.
 * <p>
 * <b>여기에만 contactPhone이 담긴다.</b> 방문 전에 연락을 해야 하는 사람은 배정된 평가사 한 명이고,
 * 그 시점에는 이 값을 받을 자격이 생긴다. 대기 목록({@link AssignableEvaluationInfo})에 담지 않은
 * 것과 같은 판단의 반대편이다.
 * <p>
 * evaluatorId를 담지 않는다. 배정받은 사람은 항상 요청자 본인이라 자기 id를 돌려받는 셈이 된다.
 * <p>
 * status를 담는다. 배정으로 상태가 바뀌지 않는다는 것(REQUESTED 그대로)이 이 설계의 결정이고,
 * 화면이 그 사실을 확인할 수 있어야 한다.
 */
public record EvaluationAssignmentInfo(
        Long evaluationId,
        String plateNumber,
        LocalDate visitDate,
        String visitAddress,
        String contactPhone,
        String status
) {

    public static EvaluationAssignmentInfo from(Evaluation evaluation) {
        return new EvaluationAssignmentInfo(
                evaluation.getId(),
                // 잠금 범위를 신청 한 건으로 묶어 두려고 배정 쿼리에 join fetch 를 붙이지 않았으므로,
                // 이 접근이 차량 조회 한 번을 낸다. 트랜잭션 안이라 프록시 초기화가 가능하다
                evaluation.getVehicle().getPlateNumber(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getContactPhone(),
                evaluation.getStatus().name()
        );
    }
}
