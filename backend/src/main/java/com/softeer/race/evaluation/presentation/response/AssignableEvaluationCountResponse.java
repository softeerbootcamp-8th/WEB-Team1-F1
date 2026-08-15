package com.softeer.race.evaluation.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 배정 대기 건수.
 * <p>
 * 숫자 하나지만 객체로 감싼다. 최상위가 원시값이면 나중에 값을 하나 더 붙일 때 응답 형태를 깨야 한다.
 */
@Schema(description = "배정 대기 건수 응답")
public record AssignableEvaluationCountResponse(

        @Schema(description = "아직 아무도 수락하지 않은 신청의 전체 수", example = "42")
        long count
) {

    public static AssignableEvaluationCountResponse from(long count) {
        return new AssignableEvaluationCountResponse(count);
    }
}
