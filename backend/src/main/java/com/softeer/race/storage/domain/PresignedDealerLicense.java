package com.softeer.race.storage.domain;

import java.time.LocalDateTime;

/** 비공개 사원증 업로드 결과. 외부 조회 주소는 의도적으로 제공하지 않는다. */
public record PresignedDealerLicense(
        String key,
        String uploadUrl,
        LocalDateTime expiresAt
) {
}
