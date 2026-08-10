package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.command.EvaluationRejectCommand;
import com.softeer.race.evaluation.domain.Evaluation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 방문 결과 반려 요청.
 * <p>
 * 사유를 필수로 받는다. 비워 둘 수 있으면 판매자는 신청이 끝난 것만 알고 왜인지는 알 수 없어,
 * 이 기능이 풀려던 문제("왜 다음 단계로 넘어가지 않는지 알 수 없다")가 그대로 남는다.
 */
@Schema(description = "방문 결과 반려 요청")
public record EvaluationRejectRequest(

        /*
         * 상한을 엔티티의 상수에서 가져온다. 여기에 숫자를 다시 적으면 컬럼 폭과 어긋나는 날
         * 검증을 통과한 값이 저장에서 잘린다.
         */
        @Schema(description = "판매자에게 전달할 반려 사유",
                example = "번호판이 등록된 차량과 일치하지 않아 매물로 등록할 수 없습니다.")
        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = Evaluation.MAX_REJECT_REASON_LENGTH,
                message = "반려 사유는 " + Evaluation.MAX_REJECT_REASON_LENGTH + "자까지 입력할 수 있습니다.")
        String reason
) {

    /**
     * 평가사 식별자를 인자로 받는다. 본문으로 받으면 남의 이름을 대고 반려할 수 있어,
     * 배정된 평가사만 결과를 낸다는 규칙이 무의미해진다.
     */
    public EvaluationRejectCommand toCommand(long evaluationId, long evaluatorId) {
        return new EvaluationRejectCommand(evaluationId, evaluatorId, reason);
    }
}
