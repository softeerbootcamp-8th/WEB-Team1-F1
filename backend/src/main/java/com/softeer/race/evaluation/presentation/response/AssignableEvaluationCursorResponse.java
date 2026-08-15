package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 다음 페이지 요청에 그대로 돌려보낼 값.
 * <p>
 * 담기는 값은 정렬이 정한다. 최신순은 id 하나로 자리가 정해져 방문일이 비어 나가고, 그 상태로
 * 돌려받는 것이 맞는 요청이다 — 비었다고 채워 보내면 정렬과 커서가 어긋난 요청이 된다.
 */
@Schema(description = "다음 페이지 요청에 그대로 돌려보낼 값")
public record AssignableEvaluationCursorResponse(

        @Schema(description = "직전 페이지 마지막 신청의 방문일. 최신순 정렬에서는 비어 있다",
                example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문일이 같을 때를 가르는 값", example = "1")
        long evaluationId
) {

    public static AssignableEvaluationCursorResponse from(AssignableEvaluationCursor cursor) {
        return new AssignableEvaluationCursorResponse(cursor.visitDate(), cursor.evaluationId());
    }
}
