package com.softeer.race.storage.presentation.response;

import com.softeer.race.storage.application.dto.info.DealerLicenseUploadInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "자동차매매사원증 업로드 주소 발급 응답")
public record DealerLicenseUploadResponse(
        @Schema(description = "회원가입 요청에 전달할 비공개 객체 키")
        String key,

        @Schema(description = "파일을 PUT할 서명된 주소")
        String uploadUrl,

        @Schema(description = "업로드 주소 만료 시각")
        LocalDateTime expiresAt
) {

    public static DealerLicenseUploadResponse from(DealerLicenseUploadInfo info) {
        return new DealerLicenseUploadResponse(info.key(), info.uploadUrl(), info.expiresAt());
    }
}
