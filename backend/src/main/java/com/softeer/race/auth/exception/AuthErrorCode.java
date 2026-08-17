package com.softeer.race.auth.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    // 없는 아이디와 틀린 비밀번호를 코드로 나누면 그 자체가 계정 열거(user enumeration) 오라클이 되므로
    // 두 실패를 하나의 코드로 합친다
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    // 만료된 세션도 이 코드로 합쳐진다. 저장소가 만료된 키를 스스로 지우기 때문에 "만료됐다"와
    // "없다"를 구분할 방법이 없고, 프론트도 코드가 아니라 401 자체로 재로그인을 유도한다
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 기능에 접근할 권한이 없습니다."),
    // 정지 사유는 담지 않는다. 관리자가 남긴 내부 기록이고, BusinessException 이 ErrorCode 의 고정
    // 메시지만 나르는 구조라 담으려면 예외 구조부터 손대야 한다
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다. 고객센터에 문의해 주세요.");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return "AUTH_" + name();
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
