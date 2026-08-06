package com.softeer.race.bid.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BidErrorCode implements ErrorCode {
    AUCTION_NOT_LIVE(HttpStatus.CONFLICT, "지금은 입찰할 수 없는 경매입니다."),
    SELLER_CANNOT_BID(HttpStatus.FORBIDDEN, "판매자는 자기 차량에 입찰할 수 없습니다."),
    EVALUATOR_CANNOT_BID(HttpStatus.FORBIDDEN, "평가사는 입찰할 수 없습니다."),
    SELF_OUTBID(HttpStatus.CONFLICT, "이미 최고가입니다."),
    BID_AMOUNT_TOO_LOW(HttpStatus.CONFLICT, "입찰 금액이 최소 금액보다 낮습니다."),
    BID_AMOUNT_NOT_ALIGNED(HttpStatus.CONFLICT, "입찰 금액이 최저 상승가 단위에 맞지 않습니다."),
    BIDDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경매입니다.");

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
