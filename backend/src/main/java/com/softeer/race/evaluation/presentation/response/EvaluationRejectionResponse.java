package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationRejectionInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 반려 처리 결과.
 * <p>
 * 차량 정보가 없다. 반려는 차량을 건드리지 않아 되비출 값이 없다 — 그 차량은 진단 전 상태 그대로
 * 남고, 판매자가 다시 신청하면 새 차량 행이 생긴다.
 */
@Schema(description = "방문 결과 반려 응답")
public record EvaluationRejectionResponse(

        @Schema(description = "방문견적 신청 ID", example = "1")
        Long evaluationId,

        @Schema(description = "신청 상태. 반려가 끝났으므로 항상 REJECTED입니다", example = "REJECTED")
        String status,

        @Schema(description = "저장된 반려 사유",
                example = "번호판이 등록된 차량과 일치하지 않아 매물로 등록할 수 없습니다.")
        String rejectReason,

        @Schema(description = "반려가 확정된 시각", example = "2026-08-05T18:00:00")
        LocalDateTime rejectedAt
) {

    public static EvaluationRejectionResponse from(EvaluationRejectionInfo info) {
        return new EvaluationRejectionResponse(
                info.evaluationId(), info.status(), info.rejectReason(), info.rejectedAt());
    }
}
