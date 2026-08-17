package com.softeer.race.user.domain;

public enum Role {
    GENERAL(true, true),
    DEALER(true, true),
    // 평가사는 서비스가 직접 위촉하는 역할이라 공개 회원가입으로 만들 수 없다
    // 이용정지 대상도 아니다 — 서비스가 위촉을 거두는 것이지 이용을 정지할 자리가 아니다
    EVALUATOR(false, false),
    /**
     * 관리자. 가입 폼으로 만들 수 있으면 아무나 스스로 관리자가 되므로 이 값은 절대 true 가 되어서는 안 된다.
     * <p>
     * 대신 <b>가입한 회원을 DB 에서 승격해</b> 만든다. 로컬과 배포가 같은 절차다.
     * 역할은 로그인할 때 세션으로 복사되므로({@code AuthenticatedUser}) 승격한 뒤 다시 로그인해야 한다.
     * 로컬은 {@code ddl-auto: create} 라 서버를 다시 띄우면 계정이 사라져 이 절차를 다시 밟아야 한다.
     * <p>
     * <b>정지할 수 없는 역할이다.</b> 이용정지는 {@code /api/admin/**} 뒤에서만 부를 수 있어 요청자가
     * 언제나 관리자인데, 여기가 false 라서 관리자는 서로도 자기 자신도 정지 대상이 될 수 없다.
     * 자기 자신인지 따로 비교하지 않는 이유가 이것이다 — 막는 것은 이 값 하나다.
     */
    ADMIN(false, false);

    private final boolean selfSignUpAllowed;
    private final boolean suspendable;

    Role(boolean selfSignUpAllowed, boolean suspendable) {
        this.selfSignUpAllowed = selfSignUpAllowed;
        this.suspendable = suspendable;
    }

    public boolean isSelfSignUpAllowed() {
        return selfSignUpAllowed;
    }

    public boolean isSuspendable() {
        return suspendable;
    }
}
