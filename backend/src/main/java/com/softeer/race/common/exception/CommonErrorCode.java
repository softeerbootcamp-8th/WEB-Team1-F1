package com.softeer.race.common.exception;

import org.springframework.http.HttpStatus;

/** 특정 도메인에 속하지 않는 에러 코드 */
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    // 낙관적 락 충돌. 서버 버그가 아니라 두 요청이 같은 데이터를 동시에 고친 결과라 500 과 갈라 둔다
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT,
            "다른 사용자가 먼저 변경했습니다. 새로 불러온 뒤 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
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
