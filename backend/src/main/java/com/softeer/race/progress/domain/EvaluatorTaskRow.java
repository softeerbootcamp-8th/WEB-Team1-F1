package com.softeer.race.progress.domain;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 평가사가 보는 방문 평가 신청 한 건
 * <p>
 * 방문 연락처는 담지 않는다. 실제로 방문하는 평가사에게 필요한 값이지만, 지금은 배정 기능이 없어
 * 미배정 신청까지 함께 내려간다 — 즉 아무 평가사나 남의 신청자 전화번호를 받아 갈 수 있다.
 * 배정이 붙어 "내 담당"이 확정된 뒤에 그 건에만 실어 보내는 것이 맞다.
 */
public record EvaluatorTaskRow(
        Long evaluationId,
        EvaluationStatus status,
        Long evaluatorId,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt,

        Long vehicleId,
        Manufacturer manufacturer,
        String model,
        Integer modelYear,
        String plateNumber,
        String sellerName
) {

    public EvaluatorTaskGroup group() {
        return EvaluatorTaskGroup.of(status, evaluatorId != null);
    }
}
