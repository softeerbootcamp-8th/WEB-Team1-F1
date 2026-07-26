package com.softeer.race.common.exception;

import org.springframework.http.HttpStatus;

/** 에러 코드, 구현체는 도메인별로 enum으로 둔다 */
public interface ErrorCode {

    /** 응답 본문의 code 값, 도메인 접두 문자열을 씀 */
    String code();

    HttpStatus status();

    /** 클라이언트에 노출되는 메시지, 내부 X */
    String message();
}
