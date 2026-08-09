package com.softeer.race.deal.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DealErrorCode implements ErrorCode {
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