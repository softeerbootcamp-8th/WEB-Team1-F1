package com.softeer.race.evaluation.domain;

/**
 * 상태별 건수 한 줄. 평가사 홈이 담당 건수를 상태별로 세는 데 쓴다.
 * <p>
 * 건수가 0인 상태는 행 자체가 나오지 않는다. group by는 있는 행만 묶기 때문이다 — 받는 쪽이
 * 없는 상태를 0으로 채운다.
 */
public record EvaluationStatusCountRow(
        EvaluationStatus status,
        long count
) {
}
