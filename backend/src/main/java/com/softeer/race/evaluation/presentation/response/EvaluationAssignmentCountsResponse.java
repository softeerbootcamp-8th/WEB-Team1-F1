package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationAssignmentCountsInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 담당 건수를 상태별로.
 * <p>
 * 목록과 나누어 둔다. 이 값을 읽는 평가사 홈은 카드가 아니라 수만 필요하다
 * ({@link AssignableEvaluationCountResponse}와 같은 이유다).
 */
@Schema(description = "평가사 담당 건수 응답")
public record EvaluationAssignmentCountsResponse(

        @Schema(description = "맡은 신청 전체 수. 아래 세 값의 합입니다", example = "7")
        long total,

        @Schema(description = "아직 진단을 쓰지 않은 건수. 담당 목록의 기본 화면(scope=ACTIVE)에 나오는 수입니다",
                example = "3")
        long pending,

        @Schema(description = "진단을 승인으로 끝낸 건수", example = "3")
        long approved,

        @Schema(description = "진단을 반려로 끝낸 건수", example = "1")
        long rejected
) {

    public static EvaluationAssignmentCountsResponse from(EvaluationAssignmentCountsInfo info) {
        return new EvaluationAssignmentCountsResponse(
                info.total(), info.pending(), info.approved(), info.rejected());
    }
}
