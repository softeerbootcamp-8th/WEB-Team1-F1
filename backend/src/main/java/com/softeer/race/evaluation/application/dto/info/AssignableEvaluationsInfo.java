package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import java.util.List;

/**
 * 배정 대기 목록 한 페이지.
 * <p>
 * 전체 건수는 담지 않는다. 목록을 나누어 읽는 동안 페이지마다 같은 수를 세는 비용이 붙는데,
 * 그 값을 쓰는 곳은 평가사 홈 한 곳이고 거기서는 목록이 필요 없다. 건수는 별도 조회로 나간다.
 */
public record AssignableEvaluationsInfo(
        List<AssignableEvaluationInfo> content,
        boolean hasNext,
        AssignableEvaluationCursor nextCursor
) {

}
