package com.softeer.race.evaluation.domain;

import java.util.Set;

public enum EvaluationStatus {

    REQUESTED,
    APPROVED,

    /**
     * 평가사가 방문 결과를 제출했다. 진단은 끝났고 판매자의 출품 동의를 기다린다.
     * <p>
     * APPROVED를 재활용하지 않는다. 그 상수는 지금 아무도 만들지 않아 의미가 비어 있고,
     * 이름만으로는 무엇을 승인한 것인지(방문인지 결과인지) 읽히지 않는다.
     */
    DIAGNOSED,

    REJECTED;

    // EnumSet이 아니라 Set.of를 쓴다. 원소가 몇 개 안 돼 성능 차이가 없고,
    // EnumSet은 가변이라 돌려받은 호출자가 내부를 바꿀 수 있다
    private static final Set<EvaluationStatus> IN_PROGRESS = Set.of(REQUESTED, APPROVED, DIAGNOSED);

    /**
     * 아직 끝나지 않은 신청의 상태들. 반려(REJECTED)만 종료 상태라, 반려된 차량은 다시 신청할 수 있다.
     * <p>
     * <b>DIAGNOSED도 진행 중이다.</b> 진단이 끝났어도 출품 동의가 남아 있어 흐름이 계속되고, 이
     * 집합이 중복 접수 차단 기준이라 빼면 진단을 마친 차를 다시 방문 신청할 수 있게 된다.
     * <p>
     * 이 판정을 서비스가 아니라 여기 두는 이유는 같은 기준을 쓸 자리가 앞으로 늘기 때문이다. 지금은
     * 중복 접수 차단 하나뿐이지만 "내 진행 중인 신청 조회"와 평가사 배정 대상 조회가 같은 집합을 본다.
     * 방문 완료 같은 상태가 추가될 때 고쳐야 할 곳도 이 한 줄로 모인다.
     */
    public static Set<EvaluationStatus> inProgress() {
        return IN_PROGRESS;
    }
}
