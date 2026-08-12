package com.softeer.race.evaluation.domain;

import java.util.Set;

public enum EvaluationStatus {

    REQUESTED,

    /**
     * 평가사가 방문 결과를 제출했고 그 결과가 승인이다. 진단은 끝났고 판매자의 출품 동의를 기다린다.
     * <p>
     * <b>승인의 대상은 방문이 아니라 평가 결과다.</b> 방문이 확정됐다는 뜻의 상태는 두지 않는다 —
     * 그 사실은 {@code Evaluation.evaluator}가 채워졌는지로 이미 드러나고, 상태에도 두면 같은 것을
     * 두 곳에서 관리하게 된다. 알림도 같은 언어를 쓴다({@code NotificationType.EVAL_APPROVED}).
     */
    APPROVED,

    REJECTED;

    // EnumSet이 아니라 Set.of를 쓴다. 원소가 몇 개 안 돼 성능 차이가 없고,
    // EnumSet은 가변이라 돌려받은 호출자가 내부를 바꿀 수 있다
    private static final Set<EvaluationStatus> IN_PROGRESS = Set.of(REQUESTED, APPROVED);

    /**
     * 아직 끝나지 않은 신청의 상태들. 반려(REJECTED)만 종료 상태라, 반려된 차량은 다시 신청할 수 있다.
     * <p>
     * <b>APPROVED도 진행 중이다.</b> 진단이 끝났어도 출품 동의가 남아 있어 흐름이 계속되고, 이
     * 집합은 진단 결과 재제출 가능 여부를 판단할 때 쓰인다. 방문견적 중복 접수는 경매 종료 여부까지
     * 함께 봐야 하므로 이 집합만으로 판단하지 않는다.
     * <p>
     * 이 판정을 서비스가 아니라 여기 두는 이유는 같은 기준을 쓸 자리가 앞으로 늘기 때문이다. 지금은
     * 진단 결과 재제출처럼 여러 상태를 하나의 흐름으로 판단하는 곳이 이 기준을 공유한다.
     * 방문 완료 같은 상태가 추가될 때 고쳐야 할 곳도 이 한 줄로 모인다.
     */
    public static Set<EvaluationStatus> inProgress() {
        return IN_PROGRESS;
    }
}
