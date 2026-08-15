package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationsInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 배정 대기 목록 한 페이지.
 * <p>
 * 배열을 그대로 내려보내지 않고 객체로 감싼다. 페이징을 붙이면서 hasNext와 nextCursor가 함께
 * 나가야 했는데, 최상위 타입이 배열이었다면 그 순간 응답 형태를 깨뜨려야 했다.
 * <p>
 * 전체 건수는 담지 않는다. 이 값을 쓰는 곳은 평가사 홈이고 거기서는 목록이 필요 없어,
 * 함께 담으면 홈이 쓰지 않을 카드 20건을 매번 받게 된다. 건수는 별도 조회로 나간다.
 */
@Schema(description = "배정 대기 목록 응답")
public record AssignableEvaluationsResponse(

        @Schema(description = "아직 아무도 수락하지 않은 신청들. 없으면 빈 배열이다")
        List<AssignableEvaluationResponse> evaluations,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 커서, 마지막 페이지면 null")
        AssignableEvaluationCursorResponse nextCursor
) {

    public static AssignableEvaluationsResponse from(AssignableEvaluationsInfo info) {
        return new AssignableEvaluationsResponse(
                info.content().stream().map(AssignableEvaluationResponse::from).toList(),
                info.hasNext(),
                info.nextCursor() != null
                        ? AssignableEvaluationCursorResponse.from(info.nextCursor())
                        : null);
    }
}
