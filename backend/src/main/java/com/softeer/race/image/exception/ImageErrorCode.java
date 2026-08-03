package com.softeer.race.image.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ImageErrorCode implements ErrorCode {

    /**
     * 장수와 파일 크기는 요청 검증(Bean Validation)이 막아 400 INVALID_REQUEST + errors 배열로 나간다.
     * 그쪽은 어느 파일이 문제인지 {@code files[2].contentLength} 형태로 짚어줄 수 있어서다.
     * 허용 형식만 여기 남는 이유는 목록이 {@code ImageContentType}과 짝이라 요청 검증으로 옮기면
     * 같은 목록을 두 곳에 적게 되기 때문이다.
     */
    UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다. jpeg, png, webp만 업로드할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ImageErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. 접두사가 없으면 UNSUPPORTED_TYPE 처럼 흔한 이름이 다른 도메인 코드와
     * 겹쳐 프론트가 처리 분기를 나눌 수 없다.
     */
    @Override
    public String code() {
        return "IMAGE_" + name();
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
