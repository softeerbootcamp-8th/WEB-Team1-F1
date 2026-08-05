package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Vehicle;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록의 한 건. 판매자의 "내 신청"과 평가사의 "내 담당"이 같은 형태를 쓴다 — 두 화면이 묻는 것이
 * "이 신청이 지금 어디까지 왔고 언제 어디로 가는가"로 같다.
 * <p>
 * <b>진단 결과를 담지 않는다.</b> 주행거리 · 시세 · 사진은 상세에서만 나간다. 목록에 넣으려면
 * 진단서와 사진을 건수만큼 더 읽어야 하는데, 목록에서 그 값들로 할 수 있는 판단이 없다.
 * <p>
 * 그래서 {@code status}가 이 목록의 핵심이다. 판매자는 DIAGNOSED를 보고 출품으로 넘어가고,
 * 평가사는 REQUESTED를 보고 아직 방문하지 않은 건을 가려낸다.
 * <p>
 * <b>contactPhone을 담지 않는다.</b> 담당 평가사에게는 필요한 값이지만 배정이 확정될 때
 * {@link EvaluationAssignmentInfo}가 이미 준다. 목록마다 다시 실어 나르면 개인정보가 로그와
 * 캐시에 남는 면만 넓어진다.
 */
public record EvaluationSummaryInfo(
        Long evaluationId,
        String status,
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt
) {

    public static EvaluationSummaryInfo from(Evaluation evaluation) {
        Vehicle vehicle = evaluation.getVehicle();

        return new EvaluationSummaryInfo(
                evaluation.getId(),
                evaluation.getStatus().name(),
                vehicle.getPlateNumber(),
                vehicle.getManufacturer(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getCreatedAt());
    }
}
