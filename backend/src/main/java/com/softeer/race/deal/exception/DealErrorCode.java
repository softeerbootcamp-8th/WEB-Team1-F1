package com.softeer.race.deal.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DealErrorCode implements ErrorCode {
    // 없는 거래와 남의 거래를 구분해 주지 않는다, 구분하면 존재 여부가 새어 나간다
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 거래입니다."),
    // 당사자이긴 하나 지금 움직일 쪽이 아니다. 거래의 존재는 이미 아는 사람이라 404 로 감출 것이 없다
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "지금은 상대방의 차례입니다."),
    // 사용자가 값을 고쳐 다시 보낼 수 있는 실패다
    PAST_TRANSPORT_SCHEDULE(HttpStatus.BAD_REQUEST, "탁송 출발 일시는 현재 시각 이후여야 합니다."),
    DELIVERY_BEFORE_TRANSPORT(HttpStatus.BAD_REQUEST, "인수 일시는 탁송 출발 일시 이후여야 합니다."),
    // 우리가 발급하지 않았거나 문서가 아닌 주소다. 업로드부터 다시 하면 해소된다
    UNMANAGED_DOCUMENT_URL(HttpStatus.BAD_REQUEST, "서류 파일 주소가 올바르지 않습니다."),
    // 모르는 형식이든 아는 형식이지만 서류로 받지 않는 형식이든 같은 코드로 답한다.
    // 발급을 요청한 사람에게는 둘 다 "이 파일로는 서류를 낼 수 없다"는 한 가지 사실이다
    UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.BAD_REQUEST, "명의이전 서류는 PDF 파일만 등록할 수 있습니다."),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "지금 단계에서는 진행할 수 없습니다."),
    NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 없는 단계입니다.");

    private final HttpStatus status;
    private final String message;

    DealErrorCode(HttpStatus status, String message) {
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
