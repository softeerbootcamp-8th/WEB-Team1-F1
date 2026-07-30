package com.softeer.race.sell.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SellErrorCode implements ErrorCode {

    VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 번호판으로 등록된 차량 정보를 찾을 수 없습니다."),

    /**
     * 정상 흐름에서는 발생하지 않는다. user_session이 users를 FK로 참조하고 인터셉터가 방금 세션을
     * 검증했으므로 계정이 없을 수 없다. 그래도 두는 이유는 이 코드가 없으면 그 상황이
     * GlobalExceptionHandler의 최후 방어선을 타고 500 INTERNAL_ERROR가 되기 때문이다.
     */
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "판매자 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    SellErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. AuctionErrorCode.VEHICLE_NOT_FOUND가 이미 접두사 없이 "VEHICLE_NOT_FOUND"를
     * 내려보내므로, 접두사가 없으면 서로 다른 원인(차량 row 없음 vs 카탈로그 미등록)이 프론트에서
     * 구별 불가능한 같은 문자열이 된다.
     */
    @Override
    public String code() {
        return "SELL_" + name();
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
