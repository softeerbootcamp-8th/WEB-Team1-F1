package com.softeer.race.dealer.domain;

public enum DealerApplicationStatus {

    /** 관리자의 심사를 기다린다. 이 상태의 신청이 있는 동안 같은 회원은 새로 신청할 수 없다. */
    PENDING,

    APPROVED,

    REJECTED;

    /**
     * 아직 결론이 나지 않은 상태들.
     * <p>
     * 값이 하나뿐인데도 집합으로 두는 이유는, 이 판정을 쓰는 쪽이 묻는 것이
     * "PENDING 인가"가 아니라 <b>"결론이 났는가"</b>이기 때문이다. 서류 보완 대기 같은 중간 상태가
     * 생기면 여기 한 줄만 늘면 되고, 호출부의 조건은 그대로 옳다.
     */
    public boolean isInProgress() {
        return this == PENDING;
    }
}
