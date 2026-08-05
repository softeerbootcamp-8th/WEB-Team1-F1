package com.softeer.race.progress.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProgressErrorCode implements ErrorCode {

    /**
     * 없는 차량과 남의 차량을 같은 코드로 번역한다. 갈라놓으면 아무 번호나 넣어보며 어떤 식별자가
     * 실재하는지 확인할 수 있고, 진행 상황에는 차량 정보와 신청 이력이 함께 실린다.
     */
    PROGRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 상황을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ProgressErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return "PROGRESS_" + name();
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
