package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 신청 목록.
 * <p>
 * 배열을 그대로 내려보내지 않고 객체로 감싼다. 나중에 총 건수나 페이징을 붙일 때 최상위 타입이
 * 배열이면 응답 형태를 깨뜨려야 한다 — {@code AssignableEvaluationsResponse}와 같은 이유다.
 */
@Schema(description = "방문견적 신청 목록 응답")
public record EvaluationSummariesResponse(

        @Schema(description = "신청 목록. 없으면 빈 배열이다")
        List<EvaluationSummaryResponse> evaluations
) {

    public static EvaluationSummariesResponse from(List<EvaluationSummaryInfo> infos) {
        return new EvaluationSummariesResponse(infos.stream()
                .map(EvaluationSummaryResponse::from)
                .toList());
    }
}
