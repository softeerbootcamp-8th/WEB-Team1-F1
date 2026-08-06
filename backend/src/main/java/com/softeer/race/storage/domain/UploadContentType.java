package com.softeer.race.storage.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.storage.exception.StorageErrorCode;

import java.util.Arrays;

/**
 * 업로드를 허용하는 형식. MIME 타입 · 확장자 · 용도 · 크기 상한을 한 곳에 묶는다.
 * <p>
 * 클라이언트에게 파일명을 받지 않고 여기 적힌 확장자로 키를 만든다. 파일명을 받으면 경로 구분자나
 * 이중 확장자가 섞인 값을 걸러내는 검증을 따로 들고 다녀야 하는데, 서버가 정하면 그 문제가 없다.
 * <p>
 * 허용 목록을 요청 검증(@Pattern)이 아니라 여기 두는 이유는 확장자 매핑과 짝이기 때문이다.
 * 두 곳에 나누면 형식을 하나 추가할 때 한쪽만 고쳐 "통과는 하는데 확장자를 모르는" 상태가 생긴다.
 * <p>
 * <b>크기 상한도 형식별로 여기서 들고 있다.</b> 요청 검증의 {@code @Max}는 필드 하나에 상수 하나라
 * 형식에 따라 다른 값을 줄 수 없다. 상한이 형식에 딸린 값이 된 이상 형식을 아는 곳이 판정해야 하고,
 * 요청 검증에는 어떤 형식이든 넘을 수 없는 절대 상한만 남는다.
 */
public enum UploadContentType {

    JPEG("image/jpeg", "jpg", FileCategory.IMAGE, UploadContentType.MAX_IMAGE_SIZE),
    PNG("image/png", "png", FileCategory.IMAGE, UploadContentType.MAX_IMAGE_SIZE),
    WEBP("image/webp", "webp", FileCategory.IMAGE, UploadContentType.MAX_IMAGE_SIZE),

    /**
     * 진단서는 스캔본이라 사진보다 크다. 여러 장을 한 파일로 묶은 스캔이 10MB를 쉽게 넘어
     * 이미지와 같은 상한을 쓰면 정상적인 진단서가 거부된다.
     */
    PDF("application/pdf", "pdf", FileCategory.DOCUMENT, UploadContentType.MAX_DOCUMENT_SIZE);

    /**
     * 현장에서 찍은 폰 사진 원본이 장당 3~8MB라 그보다 여유 있게 잡는다.
     */
    static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    /**
     * 스캔 진단서 기준이다. 파일 바이트가 서버를 통과하지 않으므로(클라이언트가 저장소로 직접
     * 올린다) 이 숫자를 키워도 서버 메모리에는 영향이 없다. 상한을 두는 것은 잘못 고른 대용량
     * 파일이 그대로 쌓이는 것을 막기 위해서다.
     */
    public static final long MAX_DOCUMENT_SIZE = 20L * 1024 * 1024;

    private final String mimeType;
    private final String extension;
    private final FileCategory category;
    private final long maxSize;

    UploadContentType(String mimeType, String extension, FileCategory category, long maxSize) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.category = category;
        this.maxSize = maxSize;
    }

    /**
     * @throws BusinessException 허용하지 않는 형식이면 400
     */
    public static UploadContentType from(String mimeType) {
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equalsIgnoreCase(mimeType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(StorageErrorCode.UNSUPPORTED_TYPE));
    }

    /**
     * @throws BusinessException 이 형식의 상한을 넘으면 400
     */
    public void validateSize(long contentLength) {
        if (contentLength > maxSize) {
            throw new BusinessException(StorageErrorCode.FILE_TOO_LARGE);
        }
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public FileCategory category() {
        return category;
    }
}
