package com.softeer.race.storage.application.dto.info;

import com.softeer.race.storage.domain.PresignedDealerLicense;
import java.time.LocalDateTime;

/** 비공개 사원증 업로드 주소 발급 결과. */
public record DealerLicenseUploadInfo(
        String key,
        String uploadUrl,
        LocalDateTime expiresAt
) {

    public static DealerLicenseUploadInfo from(PresignedDealerLicense upload) {
        return new DealerLicenseUploadInfo(upload.key(), upload.uploadUrl(), upload.expiresAt());
    }
}
