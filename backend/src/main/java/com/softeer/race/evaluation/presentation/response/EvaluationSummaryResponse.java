package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록의 한 건. 판매자의 "내 신청"과 평가사의 "내 담당"이 같은 형태를 쓴다.
 * <p>
 * 진단 결과와 사진은 없다. 상세에서 받는다.
 */
@Schema(description = "방문견적 신청 요약")
public record EvaluationSummaryResponse(

        @Schema(description = "방문견적 신청 ID. 상세 조회에 쓴다", example = "1")
        Long evaluationId,

        @Schema(description = "신청 상태. DIAGNOSED면 진단이 끝나 출품할 수 있다",
                example = "DIAGNOSED",
                allowableValues = {"REQUESTED", "APPROVED", "DIAGNOSED", "REJECTED"})
        String status,

        @Schema(description = "담당 평가사가 정해졌는지. 배정돼도 status는 REQUESTED로 남으므로 "
                + "이 값으로 구분합니다. 누가 오는지는 상세에서 확인합니다",
                example = "true")
        boolean assigned,

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "모델명", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear,

        @Schema(description = "방문 희망 날짜", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소", example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "접수 시각", example = "2026-08-05T15:30:00")
        LocalDateTime requestedAt
) {

    public static EvaluationSummaryResponse from(EvaluationSummaryInfo info) {
        return new EvaluationSummaryResponse(
                info.evaluationId(), info.status(), info.assigned(), info.plateNumber(),
                info.manufacturer(), info.model(), info.modelYear(),
                info.visitDate(), info.visitAddress(), info.requestedAt());
    }
}
