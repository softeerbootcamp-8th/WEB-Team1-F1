package com.softeer.race.auctionroom.domain;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 경매방 에러 코드
 */
public enum AuctionRoomErrorCode implements ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "경매방을 찾을 수 없습니다."),
    ROOM_NOT_OPEN_YET(HttpStatus.CONFLICT, "아직 열리지 않은 경매방입니다."),
    ROOM_ALREADY_OPEN(HttpStatus.CONFLICT, "이미 열린 경매방입니다."),
    ROOM_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 경매방입니다."),
    ROOM_STREAM_ENDED(HttpStatus.CONFLICT, "현황 전송이 끝난 경매방입니다."),
    ROOM_RESULT_NOT_READY(HttpStatus.CONFLICT, "아직 결과가 확정되지 않은 경매입니다.");

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