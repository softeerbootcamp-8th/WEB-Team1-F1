package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.vehicle.domain.VehicleKeyword;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 제출된 평가 결과. 서비스가 트랜잭션 안에서 조립해 돌려준다.
 * <p>
 * 판매자 개인정보는 담지 않는다. 방문 주소와 연락처는 결과와 무관하고, 응답에 실리면 로그·캐시로
 * 새어 나갈 경로가 늘어난다 — {@code VisitQuoteInfo}가 contactPhone을 빼는 것과 같은 이유다.
 *
 * @param submittedAt 결과가 제출된 시각. 재제출하면 갱신된다
 */
public record EvaluationResultInfo(
        Long evaluationId,
        Long vehicleId,
        String status,
        int mileage,
        long estimatedPrice,
        List<String> imageUrls,
        String diagnosticReportUrl,
        LocalDateTime submittedAt,
        List<VehicleKeyword> keywords
) {
}
