package com.softeer.race.user.domain;

public enum Role {
    GENERAL(true),
    DEALER(true),
    // 평가사는 서비스가 직접 위촉하는 역할이라 공개 회원가입으로 만들 수 없다
    EVALUATOR(false);

    private final boolean selfSignUpAllowed;

    Role(boolean selfSignUpAllowed) {
        this.selfSignUpAllowed = selfSignUpAllowed;
    }

    public boolean isSelfSignUpAllowed() {
        return selfSignUpAllowed;
    }
}
