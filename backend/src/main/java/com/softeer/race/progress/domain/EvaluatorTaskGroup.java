package com.softeer.race.progress.domain;

import com.softeer.race.evaluation.domain.EvaluationStatus;

/**
 * 평가사가 보는 신청 한 건의 자리
 */
public enum EvaluatorTaskGroup {

    /** 아직 아무 평가사도 배정되지 않았다 */
    UNASSIGNED,

    /** 나에게 배정됐고 아직 결과를 내지 않았다 */
    ASSIGNED,

    /** 내가 승인하거나 반려해 끝냈다 */
    COMPLETED;

    /**
     * 끝났는지를 배정 여부보다 먼저 본다. 배정 없이 끝나는 경로는 없지만, 데이터가 그렇게 남아도
     * 끝난 건을 "아직 아무도 안 맡음"으로 되돌려 보여주지는 않아야 한다.
     */
    public static EvaluatorTaskGroup of(EvaluationStatus status, boolean assigned) {
        if (status != EvaluationStatus.REQUESTED) {
            return COMPLETED;
        }

        return assigned ? ASSIGNED : UNASSIGNED;
    }
}
