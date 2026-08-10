package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import java.time.LocalDateTime;

/**
 * 반려 처리의 결과. 평가사 화면이 방금 무엇을 확정했는지 되비추는 데 쓴다.
 * <p>
 * 차량 정보를 담지 않는다. {@link EvaluationResultInfo}가 주행거리 · 시세 · 사진을 돌려주는 것은
 * 그 값들을 방금 이 요청이 차량에 썼기 때문인데, 반려는 차량을 건드리지 않아 되비출 것이 없다.
 * <p>
 * status를 담는다. 값이 항상 REJECTED라 없어도 될 것 같지만, 승인 응답이 status를 싣고 있어
 * 빼면 같은 화면이 두 응답을 다르게 읽어야 한다.
 *
 * @param rejectedAt 반려가 확정된 시각. 평가의 {@code updatedAt}이다 — 반려는 이 행을 마지막으로
 *                   바꾸는 사건이라 그 시각이 곧 반려 시각이다
 */
public record EvaluationRejectionInfo(
        Long evaluationId,
        String status,
        String rejectReason,
        LocalDateTime rejectedAt
) {

    public static EvaluationRejectionInfo from(Evaluation evaluation) {
        return new EvaluationRejectionInfo(
                evaluation.getId(),
                evaluation.getStatus().name(),
                evaluation.getRejectReason(),
                evaluation.getUpdatedAt());
    }
}
