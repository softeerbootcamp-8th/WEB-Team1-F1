package com.softeer.race.storage.exception;

import com.softeer.race.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum StorageErrorCode implements ErrorCode {

    /**
     * 건수는 요청 검증(Bean Validation)이 막아 400 INVALID_REQUEST + errors 배열로 나간다.
     * 그쪽은 어느 파일이 문제인지 {@code files[2].contentLength} 형태로 짚어줄 수 있어서다.
     * 허용 형식이 여기 남는 이유는 목록이 {@code UploadContentType}과 짝이라 요청 검증으로 옮기면
     * 같은 목록을 두 곳에 적게 되기 때문이다.
     */
    UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST,
            "지원하지 않는 파일 형식입니다. jpeg, png, webp, pdf만 업로드할 수 있습니다."),

    /**
     * 크기도 형식과 함께 여기로 왔다. 상한이 형식마다 달라진 뒤로는 요청 검증이 판정할 수 없다 —
     * {@code @Max}는 필드 하나에 상수 하나라 같은 필드에 이미지 10MB와 문서 20MB를 함께 걸 수 없다.
     * <p>
     * 대신 요청 검증에는 절대 상한(가장 큰 형식의 상한)이 남아 있어, 어떤 형식으로도 통과할 수 없는
     * 크기는 여전히 {@code files[1].contentLength} 형태로 어느 파일인지 짚어준다. 여기까지 오는
     * 것은 <b>절대 상한은 통과했지만 그 형식에는 큰</b> 경우뿐이다(예: 15MB짜리 JPEG).
     */
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST,
            "파일 크기가 허용 범위를 넘었습니다. 이미지는 10MB, 문서는 20MB까지 업로드할 수 있습니다."),

    UNSUPPORTED_DEALER_LICENSE_TYPE(HttpStatus.BAD_REQUEST,
            "자동차매매사원증은 jpeg, png, pdf 형식만 업로드할 수 있습니다."),

    DEALER_LICENSE_TOO_LARGE(HttpStatus.BAD_REQUEST,
            "자동차매매사원증은 10MB까지 업로드할 수 있습니다."),

    /**
     * 조회 주소를 발급할 수 없는 키다. 500으로 두는 이유는 이 값이 요청 본문이 아니라 DB에 저장된
     * 사원증 키이기 때문이다 — 관리자가 잘못 보낸 것이 아니라 우리가 발급한 적 없는 키가 저장돼
     * 있다는 뜻이라, 사용자가 고칠 수 있는 오류가 아니다.
     */
    INVALID_DEALER_LICENSE_KEY(HttpStatus.INTERNAL_SERVER_ERROR,
            "사원증 파일 주소를 만들 수 없습니다."),

    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "파일 저장소를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    StorageErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 접두사를 붙인다. 접두사가 없으면 UNSUPPORTED_TYPE 처럼 흔한 이름이 다른 도메인 코드와
     * 겹쳐 프론트가 처리 분기를 나눌 수 없다.
     */
    @Override
    public String code() {
        return "STORAGE_" + name();
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
