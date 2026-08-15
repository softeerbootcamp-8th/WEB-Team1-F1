package com.softeer.race.deal.application.dto;

import com.softeer.race.storage.domain.PresignedUpload;

import java.time.LocalDateTime;

/**
 * 판매 서류 한 건의 발급 결과
 * <p>
 * 저장소의 {@code PresignedUpload} 를 그대로 내보내지 않는다. 서류는 한 번에 한 건이라 목록으로
 * 감쌀 이유가 없고, 거래가 저장소 타입을 응답까지 실어 나르면 저장소 표현이 바뀔 때 거래 API 가
 * 함께 흔들린다.
 */
public record DealDocumentUploadInfo(
        String key,
        String uploadUrl,
        String fileUrl,
        LocalDateTime expiresAt
) {

    public static DealDocumentUploadInfo from(PresignedUpload upload) {
        return new DealDocumentUploadInfo(
                upload.key(), upload.uploadUrl(), upload.fileUrl(), upload.expiresAt());
    }
}
