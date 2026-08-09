package com.softeer.race.deal.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DealErrorCode implements ErrorCode {
    // 없는 거래와 남의 거래를 구분해 주지 않는다, 구분하면 존재 여부가 새어 나간다
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 거래입니다."),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "지금 단계에서는 진행할 수 없습니다."),
    NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 없는 단계입니다.");

    private final HttpStatus status;
    private final String message;

    DealErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}