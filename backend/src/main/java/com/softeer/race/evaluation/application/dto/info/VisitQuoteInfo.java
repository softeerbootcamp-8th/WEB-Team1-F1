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
 */
public record VisitQuoteInfo(
        Long evaluationId,
        Long vehicleId,
        String plateNumber,
        LocalDate visitDate,
        String visitAddress,
        String status,
        long estimatedPrice
) {

    /**
     * @param estimatedPrice 차량에 저장한 값과 같은 값이어야 한다. Evaluation에서 다시 꺼내지 않고
     *                       인자로 받는 것은 Evaluation이 금액을 들고 있지 않기 때문이다
     */
    public static VisitQuoteInfo from(Evaluation evaluation, long estimatedPrice) {
        Vehicle vehicle = evaluation.getVehicle();

        return new VisitQuoteInfo(
                evaluation.getId(),
                vehicle.getId(),
                vehicle.getPlateNumber(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getStatus().name(),
                estimatedPrice
        );
    }
}
