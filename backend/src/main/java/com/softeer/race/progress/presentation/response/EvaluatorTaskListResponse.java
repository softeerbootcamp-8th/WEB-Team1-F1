package com.softeer.race.progress.presentation.response;

import com.softeer.race.progress.application.dto.EvaluatorTaskInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "평가사의 일감 목록")
public record EvaluatorTaskListResponse(

        @Schema(description = "방문 희망일이 빠른 것부터. 아직 아무도 맡지 않은 신청과 내가 맡은 신청이 섞여 있고, "
                + "구분은 각 항목의 group으로 한다")
        List<EvaluatorTaskResponse> content
) {

    public static EvaluatorTaskListResponse from(List<EvaluatorTaskInfo> infos) {
        return new EvaluatorTaskListResponse(
                infos.stream().map(EvaluatorTaskResponse::from).toList());
    }
}
