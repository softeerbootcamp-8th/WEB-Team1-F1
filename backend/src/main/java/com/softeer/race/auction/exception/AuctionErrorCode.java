package com.softeer.race.auction.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuctionErrorCode implements ErrorCode {

    INVALID_START_AT(HttpStatus.BAD_REQUEST, "경매 시작 시각은 현재보다 1시간 이후여야 합니다."),
    VEHICLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 차량입니다."),
    AUCTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 경매가 있는 차량입니다.");

    private final HttpStatus status;
    private final String message;

    AuctionErrorCode(HttpStatus status, String message) {
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
