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
    private static final Set<EvaluationStatus> DIAGNOSIS_PENDING = Set.of(REQUESTED);
    private static final Set<EvaluationStatus> DIAGNOSIS_COMPLETED = Set.of(APPROVED, REJECTED);

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

    /**
     * 평가사가 아직 진단을 쓰지 않은 상태들. 담당 목록의 기본 화면이 이 기준으로 좁혀진다.
     * <p>
     * <b>{@link #inProgress()}와 다른 축이다.</b> 저쪽은 "신청이 끝났는가"를 묻고 APPROVED를
     * 진행 중으로 본다 — 출품 동의가 남아 있기 때문이다. 여기서 묻는 것은 "평가사가 할 일이
     * 남았는가"라서 APPROVED가 빠진다. 두 기준을 한 집합으로 합치면 판매자의 흐름과 평가사의
     * 할 일이 같은 이름을 쓰게 되어, 한쪽이 바뀔 때 다른 쪽이 조용히 따라 바뀐다.
     */
    public static Set<EvaluationStatus> diagnosisPending() {
        return DIAGNOSIS_PENDING;
    }

    /**
     * 평가사가 손을 뗀 상태들. 승인과 반려를 함께 묶는다.
     * <p>
     * 두 결말이 판매자에게는 정반대지만 <b>평가사의 담당 목록에서는 같은 칸</b>이다. 이 목록이
     * 가르는 것은 결과의 좋고 나쁨이 아니라 "오늘 더 손댈 일이 있는가"이고, 그 답은 둘 다
     * "없다"이다. 승인은 경매 등록 전까지 재제출할 수 있어 목록에서 지우지 않고 완료 쪽으로
     * 옮겨만 둔다.
     */
    public static Set<EvaluationStatus> diagnosisCompleted() {
        return DIAGNOSIS_COMPLETED;
    }
}
