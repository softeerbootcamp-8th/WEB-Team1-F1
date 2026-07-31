package com.softeer.race.quote.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 시세 조회 에러 코드
 */
public enum QuoteErrorCode implements ErrorCode {

    /**
     * 미등록 번호판과 소유자명 불일치를 하나의 코드로 합친다. 갈라두면 번호판을 바꿔 넣어보며
     * 소유자명을 알아낼 수 있다. 메시지도 어느 쪽이 틀렸는지 알려주지 않는다.
     */
    QUOTE_VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "차량 정보를 찾을 수 없습니다. 번호판과 이름을 확인해 주세요.");

    private final HttpStatus status;
    private final String message;

    QuoteErrorCode(HttpStatus status, String message) {
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
