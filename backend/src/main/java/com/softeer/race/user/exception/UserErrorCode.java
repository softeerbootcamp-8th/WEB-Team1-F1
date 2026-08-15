package com.softeer.race.user.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 휴대전화 번호입니다."),
    DUPLICATE_DEALER_LICENSE(HttpStatus.CONFLICT, "이미 등록된 자동차매매사원증입니다."),
    UNSUPPORTED_SIGNUP_ROLE(HttpStatus.BAD_REQUEST, "해당 유형으로는 회원가입할 수 없습니다."),
    DEALER_LICENSE_REQUIRED(HttpStatus.BAD_REQUEST, "딜러 회원가입에는 자동차매매사원증이 필요합니다."),
    DEALER_LICENSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "일반 회원가입에는 자동차매매사원증을 등록할 수 없습니다."),
    INVALID_DEALER_LICENSE(HttpStatus.BAD_REQUEST, "유효한 자동차매매사원증 파일이 아닙니다.");

    private final HttpStatus status;
    private final String message;

    UserErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return "USER_" + name();
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
