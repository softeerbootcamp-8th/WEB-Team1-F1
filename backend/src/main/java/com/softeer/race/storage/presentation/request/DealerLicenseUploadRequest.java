package com.softeer.race.storage.presentation.request;

import com.softeer.race.storage.domain.UploadContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "자동차매매사원증 업로드 주소 발급 요청")
public record DealerLicenseUploadRequest(

        @Schema(description = "파일 MIME 타입", example = "image/jpeg",
                allowableValues = {"image/jpeg", "image/png", "application/pdf"})
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType,

        @Schema(description = "파일 크기(바이트), 최대 10MB", example = "2481920")
        @Positive(message = "contentLength는 0보다 커야 합니다.")
        @Max(value = UploadContentType.MAX_DEALER_LICENSE_SIZE,
                message = "자동차매매사원증은 10MB를 넘을 수 없습니다.")
        long contentLength
) {
}
