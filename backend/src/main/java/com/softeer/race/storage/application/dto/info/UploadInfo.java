package com.softeer.race.storage.application.dto.info;

import com.softeer.race.storage.domain.PresignedUpload;

import java.util.List;

/**
 * 발급 결과. 요청한 파일 순서와 같은 순서로 담긴다.
 */
public record UploadInfo(List<PresignedUpload> uploads) {
}
