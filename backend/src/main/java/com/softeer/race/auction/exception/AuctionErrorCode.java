package com.softeer.race.auction.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum AuctionErrorCode implements ErrorCode {

    INVALID_START_AT(BAD_REQUEST, "경매 시작 시각은 현재보다 1시간 이후여야 합니다."),
    VEHICLE_NOT_FOUND(NOT_FOUND, "존재하지 않는 차량입니다."),
    AUCTION_ALREADY_EXISTS(CONFLICT, "이미 등록된 경매가 있는 차량입니다."),
    NOT_VEHICLE_OWNER(FORBIDDEN, "본인 소유 차량만 경매로 등록할 수 있습니다."),
    AUCTION_NOT_FOUND(NOT_FOUND, "존재하지 않는 경매입니다."),
    NOT_AUCTION_SELLER(FORBIDDEN, "본인이 등록한 경매만 처리 할 수 있습니다."),
    AUCTION_ROOM_ALREADY_OPEN(CONFLICT, "이미 경매방이 열린 경매는 수정할 수 없습니다."),
    AUCTION_NOT_ENDED(CONFLICT, "종료되지 않은 경매는 삭제할 수 없습니다."),
    VEHICLE_NOT_APPROVED(CONFLICT, "평가사 승인이 완료된 차량만 경매로 등록할 수 있습니다."),
    START_ALERT_NOT_OPEN(CONFLICT, "시작 전인 경매만 시작 알림을 신청할 수 있습니다.");

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
