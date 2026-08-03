package com.softeer.race.image.domain;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.image.exception.ImageErrorCode;

import java.util.Arrays;

/**
 * 업로드를 허용하는 이미지 형식. MIME 타입과 확장자를 한 곳에 묶는다.
 * <p>
 * 클라이언트에게 파일명을 받지 않고 여기 적힌 확장자로 키를 만든다. 파일명을 받으면 경로 구분자나
 * 이중 확장자가 섞인 값을 걸러내는 검증을 따로 들고 다녀야 하는데, 서버가 정하면 그 문제가 없다.
 * <p>
 * 허용 목록을 요청 검증(@Pattern)이 아니라 여기 두는 이유는 확장자 매핑과 짝이기 때문이다.
 * 두 곳에 나누면 형식을 하나 추가할 때 한쪽만 고쳐 "통과는 하는데 확장자를 모르는" 상태가 생긴다.
 */
public enum ImageContentType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageContentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    /**
     * @throws BusinessException 허용하지 않는 형식이면 400
     */
    public static ImageContentType from(String mimeType) {
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equalsIgnoreCase(mimeType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ImageErrorCode.UNSUPPORTED_TYPE));
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }
}
