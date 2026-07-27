package com.softeer.race.user.domain;

public enum Role {
    GENERAL(true),
    DEALER(true),
    EVALUATOR(false);

    private final boolean selfSignUpAllowed;

    Role(boolean selfSignUpAllowed) {
        this.selfSignUpAllowed = selfSignUpAllowed;
    }

    public boolean isSelfSignUpAllowed() {
        return selfSignUpAllowed;
    }
}
