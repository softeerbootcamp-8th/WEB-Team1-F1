package com.softeer.race.deal.presentation.response;

import com.softeer.race.deal.application.dto.DealDocumentUploadInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "판매 서류 업로드 주소 발급 응답")
public record DealDocumentUploadResponse(

        @Schema(description = "저장소 안의 객체 키",
                example = "documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        String key,

        @Schema(description = "이 주소로 파일을 PUT 합니다. 발급 요청에 적은 Content-Type 과 크기를 "
                + "그대로 보내야 하며, 다르면 업로드가 거부됩니다.")
        String uploadUrl,

        @Schema(description = "업로드 후 서류를 조회할 주소. 서류·탁송 일정 제출의 documentUrl 로 "
                + "보내야 하는 값입니다.",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        String fileUrl,

        @Schema(description = "uploadUrl 이 만료되는 시각", example = "2026-08-12T15:30:00")
        LocalDateTime expiresAt
) {

    public static DealDocumentUploadResponse from(DealDocumentUploadInfo info) {
        return new DealDocumentUploadResponse(
                info.key(), info.uploadUrl(), info.fileUrl(), info.expiresAt());
    }
}
