package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 방문견적 신청 결과. status가 REQUESTED이고 배정된 평가사가 없다는 것이 "접수됨, 배정 대기"다.
 * <p>
 * contactPhone은 넣지 않는다. 신청자가 방금 보낸 값이고, 응답에 실리면 개인정보가 로그·캐시로
 * 새어 나갈 경로가 늘어난다. 기준가도 넣지 않는다 — 예상 시세와 나란히 놓으면 감가율이 역산된다.
 */
@Schema(description = "방문견적 신청 응답")
public record VisitQuoteResponse(

        @Schema(description = "접수된 방문견적 신청 ID", example = "1")
        Long evaluationId,

        @Schema(description = "등록된 차량 ID", example = "1000")
        Long vehicleId,

        @Schema(description = "신청한 차량의 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "방문 희망 날짜", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소", example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "신청 상태. 접수 직후에는 평가사 배정 대기를 뜻하는 REQUESTED다", example = "REQUESTED")
        String status,

        @Schema(description = "서버가 산정한 예상 시세", example = "23200000")
        long estimatedPrice
) {

    public static VisitQuoteResponse from(VisitQuoteInfo info) {
        return new VisitQuoteResponse(
                info.evaluationId(), info.vehicleId(), info.plateNumber(),
                info.visitDate(), info.visitAddress(),
                info.status(), info.estimatedPrice());
    }
}
