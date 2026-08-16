package com.softeer.race.dealer.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DealerApplicationErrorCode implements ErrorCode {

    LICENSE_REQUIRED(HttpStatus.BAD_REQUEST, "딜러 심사 신청에는 자동차매매사원증이 필요합니다."),
    INVALID_LICENSE(HttpStatus.BAD_REQUEST, "유효한 자동차매매사원증 파일이 아닙니다."),
    DUPLICATE_LICENSE(HttpStatus.CONFLICT, "이미 등록된 자동차매매사원증입니다."),
    ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 심사 중인 신청이 있습니다."),
    APPLICANT_NOT_FOUND(HttpStatus.NOT_FOUND, "신청자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    DealerApplicationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return "DEALER_APPLICATION_" + name();
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
