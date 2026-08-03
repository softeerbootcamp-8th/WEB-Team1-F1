package com.softeer.race.evaluation.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EvaluationImageErrorCode implements ErrorCode {

    /**
     * 장수와 파일 크기는 요청 검증(Bean Validation)이 막아 400 INVALID_REQUEST + errors 배열로 나간다.
     * 그쪽은 어느 파일이 문제인지 {@code files[2].contentLength} 형태로 짚어줄 수 있어서다.
     * 허용 형식만 여기 남는 이유는 목록이 {@code ImageContentType}과 짝이라 요청 검증으로 옮기면
     * 같은 목록을 두 곳에 적게 되기 때문이다.
     */
    UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다. jpeg, png, webp만 업로드할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    EvaluationImageErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. 평가 도메인에는 곧 평가 요청·평가서 관련 코드가 함께 생기는데, 사진 업로드
     * 실패와 그쪽 실패가 프론트에서 같은 문자열로 보이면 처리 분기를 나눌 수 없다.
     */
    @Override
    public String code() {
        return "EVAL_IMAGE_" + name();
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
