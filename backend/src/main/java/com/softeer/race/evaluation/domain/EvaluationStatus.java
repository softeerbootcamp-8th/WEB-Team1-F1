package com.softeer.race.evaluation.domain;

import java.util.Set;

public enum EvaluationStatus {
    REQUESTED,
    APPROVED,
    REJECTED;

    // EnumSet이 아니라 Set.of를 쓴다. 원소가 둘뿐이라 성능 차이가 없고,
    // EnumSet은 가변이라 돌려받은 호출자가 내부를 바꿀 수 있다
    private static final Set<EvaluationStatus> IN_PROGRESS = Set.of(REQUESTED, APPROVED);

    /**
     * 아직 끝나지 않은 신청의 상태들. 반려(REJECTED)만 종료 상태라, 반려된 차량은 다시 신청할 수 있다.
     * <p>
     * 이 판정을 서비스가 아니라 여기 두는 이유는 같은 기준을 쓸 자리가 앞으로 늘기 때문이다. 지금은
     * 중복 접수 차단 하나뿐이지만 "내 진행 중인 신청 조회"와 평가사 배정 대상 조회가 같은 집합을 본다.
     * 방문 완료·진단 완료 같은 상태가 추가될 때 고쳐야 할 곳도 이 한 줄로 모인다.
     */
    public static Set<EvaluationStatus> inProgress() {
        return IN_PROGRESS;
    }
}
