package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "다음 페이지 요청에 그대로 돌려보낼 값")
public record AssignableEvaluationCursorResponse(

        @Schema(description = "직전 페이지 마지막 신청의 방문일", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문일이 같을 때를 가르는 값", example = "1")
        long evaluationId
) {

    public static AssignableEvaluationCursorResponse from(AssignableEvaluationCursor cursor) {
        return new AssignableEvaluationCursorResponse(cursor.visitDate(), cursor.evaluationId());
    }
}
