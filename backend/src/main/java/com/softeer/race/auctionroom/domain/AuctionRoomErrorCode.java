package com.softeer.race.auctionroom.domain;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 경매방 에러 코드
 */
public enum AuctionRoomErrorCode implements ErrorCode {

    AUCTION_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "경매방을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    AuctionRoomErrorCode(HttpStatus status, String message) {
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