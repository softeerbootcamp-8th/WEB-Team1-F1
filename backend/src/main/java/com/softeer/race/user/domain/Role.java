package com.softeer.race.user.domain;

public enum Role {
    GENERAL(true),
    DEALER(true),
    // 평가사는 서비스가 직접 위촉하는 역할이라 공개 회원가입으로 만들 수 없다
    EVALUATOR(false),
    /**
     * 관리자. 가입 폼으로 만들 수 있으면 아무나 스스로 관리자가 되므로 이 값은 절대 true 가 되어서는 안 된다.
     * <p>
     * 대신 <b>가입한 회원을 DB 에서 승격해</b> 만든다. 로컬과 배포가 같은 절차다.
     * 역할은 로그인할 때 세션으로 복사되므로({@code AuthenticatedUser}) 승격한 뒤 다시 로그인해야 한다.
     * 로컬은 {@code ddl-auto: create} 라 서버를 다시 띄우면 계정이 사라져 이 절차를 다시 밟아야 한다.
     */
    ADMIN(false);

    private final boolean selfSignUpAllowed;

    Role(boolean selfSignUpAllowed) {
        this.selfSignUpAllowed = selfSignUpAllowed;
    }

    public boolean isSelfSignUpAllowed() {
        return selfSignUpAllowed;
    }
}
