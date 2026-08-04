package com.softeer.race.image.presentation.response;

import com.softeer.race.image.application.dto.info.ImageUploadInfo;
import com.softeer.race.image.domain.PresignedUpload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발급 결과. 요청한 파일 순서와 같은 순서로 담긴다.
 */
@Schema(description = "이미지 업로드 주소 발급 응답")
public record ImageUploadResponse(

        @Schema(description = "요청한 순서대로의 발급 결과")
        List<PresignedUploadResponse> uploads
) {

    public static ImageUploadResponse from(ImageUploadInfo info) {
        return new ImageUploadResponse(info.uploads().stream()
                .map(PresignedUploadResponse::from)
                .toList());
    }

    @Schema(description = "발급된 업로드 한 건")
    public record PresignedUploadResponse(

            @Schema(description = "저장소 안의 객체 키",
                    example = "images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg")
            String key,

            @Schema(description = "이 주소로 파일을 PUT 합니다. 요청에 적은 Content-Type과 크기를 "
                    + "그대로 보내야 하며, 다르면 업로드가 거부됩니다.")
            String uploadUrl,

            @Schema(description = "업로드 후 이미지를 조회할 주소. 저장해 두어야 하는 값입니다.",
                    example = "https://www.f1race.site/images/2026/08/"
                            + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg")
            String fileUrl,

            @Schema(description = "uploadUrl이 만료되는 시각", example = "2026-08-02T15:30:00")
            LocalDateTime expiresAt
    ) {

        private static PresignedUploadResponse from(PresignedUpload upload) {
            return new PresignedUploadResponse(
                    upload.key(), upload.uploadUrl(), upload.fileUrl(), upload.expiresAt());
        }
    }
}
