package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import com.softeer.race.evaluation.application.dto.AssignableEvaluationSort;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 어떤 순서로, 어디부터 읽을지. 커서는 직전 응답의 nextCursor를 그대로 돌려받는다.
 * <p>
 * <b>커서에 정렬을 다시 담지 않는다.</b> 정렬마다 커서의 모양이 달라, 요청의 sort와 커서의 값
 * 구성이 맞는지만 보면 어긋난 짝이 전부 걸린다. 같은 뜻을 두 곳에 적으면 둘이 다를 때 무엇을
 * 믿을지 정하는 규칙이 하나 더 필요해진다.
 */
public record AssignableEvaluationCursorRequest(

        // 없으면 기본 정렬이다. 목록을 처음 여는 요청에까지 값을 요구할 이유가 없다
        AssignableEvaluationSort sort,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate visitDate,

        Long evaluationId
) {

    // 이어 읽을 지점을 특정할 수 없는 커서를 막는다. 조용히 첫 페이지를 주면 "더 보기"를 눌렀는데
    // 목록이 처음으로 되감기고, 정렬을 바꾸면서 이전 커서를 그대로 보낸 경우에는 두 순서가 섞인다.
    //
    // 정렬을 바꾸며 옛 커서를 보내는 일이 바로 이 검사에 걸린다. 방문일 순은 방문일이 있어야 하고
    // 최신순은 방문일을 쓰지 않으므로, 어느 방향으로 바꾸든 모양이 맞지 않는다.
    @AssertTrue(message = "커서는 정렬이 요구하는 값을 모두 갖추어야 합니다.")
    boolean isCursorConsistent() {
        if (isFirstPage()) {
            return true;
        }
        return sortOrDefault() == AssignableEvaluationSort.LATEST
                ? visitDate == null && evaluationId != null
                : visitDate != null && evaluationId != null;
    }

    /**
     * 커서가 없으면 null을 준다. 첫 페이지라는 뜻이다.
     */
    public AssignableEvaluationCursor toCursor() {
        return isFirstPage() ? null : new AssignableEvaluationCursor(visitDate, evaluationId);
    }

    public AssignableEvaluationSort sortOrDefault() {
        return sort != null ? sort : AssignableEvaluationSort.VISIT_DATE;
    }

    // 커서 자리가 통째로 비어 있을 때만 첫 페이지다. sort는 커서가 아니라 정렬 선택이라 보지 않는다
    private boolean isFirstPage() {
        return visitDate == null && evaluationId == null;
    }
}
