package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.domain.EvaluationStatusCountRow;
import java.util.List;

/**
 * 평가사가 맡은 건수를 상태별로 나눈 것. 평가사 홈이 목록 대신 이 값을 읽는다.
 * <p>
 * <b>목록에서 세지 않는다.</b> 담당 목록이 진행 중과 완료로 갈리면서, 어느 한쪽을 받아도
 * 나머지를 셀 수 없게 됐다. 홈이 두 목록을 다 받아 합치면 카드 세 칸의 숫자를 위해 목록 두 벌을
 * 실어 나르게 된다.
 * <p>
 * {@code total}을 따로 담는다. 세 값의 합이지만 받는 쪽이 더하게 두면 상태가 하나 늘 때 홈의
 * 합계가 조용히 틀린다 — 서버가 아는 값을 화면이 다시 계산할 이유가 없다.
 */
public record EvaluationAssignmentCountsInfo(
        long total,
        long pending,
        long approved,
        long rejected
) {

    /**
     * 건수가 0인 상태는 group by 결과에 행이 없다. 그 자리를 0으로 채우는 것이 이 변환의 일이다.
     */
    public static EvaluationAssignmentCountsInfo from(List<EvaluationStatusCountRow> rows) {
        long pending = countOf(rows, EvaluationStatus.REQUESTED);
        long approved = countOf(rows, EvaluationStatus.APPROVED);
        long rejected = countOf(rows, EvaluationStatus.REJECTED);

        return new EvaluationAssignmentCountsInfo(pending + approved + rejected,
                pending, approved, rejected);
    }

    private static long countOf(List<EvaluationStatusCountRow> rows, EvaluationStatus status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .mapToLong(EvaluationStatusCountRow::count)
                .findFirst()
                .orElse(0L);
    }
}
