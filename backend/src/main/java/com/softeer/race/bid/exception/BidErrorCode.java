package com.softeer.race.bid.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 입찰 도메인 에러 코드 */
public enum BidErrorCode implements ErrorCode {

    // 구간표에 구멍이 났다는 뜻이므로 클라이언트가 고칠 수 있는 것이 없다, 상세 원인은 로그에만 남긴다
    BID_INCREMENT_TIER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "입찰 기준을 확인할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    BidErrorCode(HttpStatus status, String message) {
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
