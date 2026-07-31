package com.softeer.race.common.exception;

/** 비즈니스 규칙 위반, 도메인별 예외 클래스를 만들지 않고 ErrorCode로 구분한다 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
