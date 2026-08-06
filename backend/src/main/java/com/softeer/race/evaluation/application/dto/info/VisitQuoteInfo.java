package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.vehicle.domain.Vehicle;
import java.time.LocalDate;

/**
 * 서비스 계층 반환값. 엔티티를 웹 계층에 노출하지 않기 위해 트랜잭션 안에서 변환한다.
 * <p>
 * contactPhone을 담지 않는다. 응답 개인정보 비노출선을 SignUpInfo · AuthUserInfo와 같게 유지한다 —
 * 신청자가 방금 보낸 값이라 되돌려줄 이유가 없고, 필드가 있으면 로그·캐시로 새어 나갈 경로가 늘어난다.
 * <p>
 * evaluator를 담지 않는다. 접수 직후에는 항상 비어 있어 담아도 null만 나간다.
 * <p>
 * <b>예상 시세도 담지 않는다.</b> 접수 시점에는 산정하지 않는다 — 주행거리를 모르는 상태의 금액은
 * 아무것도 보증하지 않고, 화면에 뜨면 사용자는 그것을 평가사가 제시할 금액으로 읽는다.
 * 시세는 평가사가 방문해 산정한다.
 */
public record VisitQuoteInfo(
        Long evaluationId,
        Long vehicleId,
        String plateNumber,
        LocalDate visitDate,
        String visitAddress,
        String status
) {

    public static VisitQuoteInfo from(Evaluation evaluation) {
        Vehicle vehicle = evaluation.getVehicle();

        return new VisitQuoteInfo(
                evaluation.getId(),
                vehicle.getId(),
                vehicle.getPlateNumber(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getStatus().name()
        );
    }
}
