package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 제출된 평가 결과.
 * <p>
 * 판매자 개인정보(방문 주소 · 연락처)는 담지 않는다. 결과와 무관하고, 응답에 실리면 로그·캐시로
 * 새어 나갈 경로가 늘어난다.
 */
@Schema(description = "평가 결과 응답")
public record EvaluationResultResponse(

        @Schema(description = "평가 ID", example = "1")
        Long evaluationId,

        @Schema(description = "진단이 반영된 차량 ID", example = "1000")
        Long vehicleId,

        @Schema(description = "평가 상태. 결과가 제출되면 DIAGNOSED가 됩니다", example = "DIAGNOSED")
        String status,

        @Schema(description = "실측 주행거리(km)", example = "45000")
        int mileage,

        @Schema(description = "산정된 예상 시세(원)", example = "21500000")
        long estimatedPrice,

        @Schema(description = "등록된 차량 사진. 보낸 순서 그대로이며 첫 번째가 대표 이미지입니다")
        List<String> imageUrls,

        @Schema(description = "진단서 PDF 주소",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        String diagnosticReportUrl,

        @Schema(description = "결과가 제출된 시각. 다시 제출하면 갱신됩니다",
                example = "2026-08-05T15:30:00")
        LocalDateTime submittedAt
) {

    public static EvaluationResultResponse from(EvaluationResultInfo info) {
        return new EvaluationResultResponse(
                info.evaluationId(), info.vehicleId(), info.status(),
                info.mileage(), info.estimatedPrice(), info.imageUrls(),
                info.diagnosticReportUrl(), info.submittedAt());
    }
}
