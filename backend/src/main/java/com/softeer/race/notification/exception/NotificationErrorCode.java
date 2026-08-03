package com.softeer.race.notification.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {

    /**
     * 없는 알림과 남의 알림을 같은 코드로 답한다. 권한 문제로 나누면 그 알림이 존재한다는 사실이
     * 드러나는데, 알림 본문은 남의 거래 상황을 담고 있다.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    NotificationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. BidErrorCode 등 먼저 만들어진 코드가 접두사 없이 NOT_FOUND 계열을 내보내고
     * 있어, 붙이지 않으면 프론트에서 어느 도메인의 실패인지 구별되지 않는다.
     */
    @Override
    public String code() {
        return "NOTIFICATION_" + name();
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