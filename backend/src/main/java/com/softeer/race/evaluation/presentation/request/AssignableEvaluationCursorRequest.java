package com.softeer.race.evaluation.presentation.request;

import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 직전 응답의 nextCursor를 그대로 돌려받는다.
 */
public record AssignableEvaluationCursorRequest(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate visitDate,

        Long evaluationId
) {

    // 한쪽만 온 커서로는 이어 읽을 지점을 특정할 수 없다.
    // 조용히 첫 페이지를 주면 "더 보기"를 눌렀는데 목록이 처음으로 되감겨 더 헷갈린다.
    @AssertTrue(message = "커서 정보는 전부 있거나 전부 없어야 합니다.")
    boolean isCursorConsistent() {
        return isEmpty() || isComplete();
    }

    /**
     * 커서가 없으면 null을 준다. 첫 페이지라는 뜻이다.
     */
    public AssignableEvaluationCursor toCursor() {
        return isEmpty() ? null : new AssignableEvaluationCursor(visitDate, evaluationId);
    }

    // 첫 페이지
    private boolean isEmpty() {
        return visitDate == null && evaluationId == null;
    }

    private boolean isComplete() {
        return visitDate != null && evaluationId != null;
    }
}
