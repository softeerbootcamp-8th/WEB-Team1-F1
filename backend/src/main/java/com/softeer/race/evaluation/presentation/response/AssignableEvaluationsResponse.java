package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 배정 대기 목록.
 * <p>
 * 배열을 그대로 내려보내지 않고 객체로 감싼다. 나중에 총 건수나 페이징을 붙일 때 최상위 타입이
 * 배열이면 응답 형태를 깨뜨려야 하고, {@code AuctionListResponse}도 같은 이유로 감싸고 있다.
 */
@Schema(description = "배정 대기 목록 응답")
public record AssignableEvaluationsResponse(

        @Schema(description = "아직 아무도 수락하지 않은 신청들. 없으면 빈 배열이다")
        List<AssignableEvaluationResponse> evaluations
) {

    public static AssignableEvaluationsResponse from(List<AssignableEvaluationInfo> infos) {
        return new AssignableEvaluationsResponse(infos.stream()
                .map(AssignableEvaluationResponse::from)
                .toList());
    }
}
