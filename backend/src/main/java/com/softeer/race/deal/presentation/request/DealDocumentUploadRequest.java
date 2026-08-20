package com.softeer.race.deal.presentation.request;

import com.softeer.race.storage.domain.UploadContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 명의이전 서류 업로드 주소 발급 요청
 * <p>
 * 한 건만 받는다. 서류는 거래당 한 장이라 목록으로 받으면 "여러 건을 보냈을 때 어느 것을
 * 서류로 삼는가"라는 답할 필요 없는 질문이 생긴다.
 * <p>
 * 파일명을 받지 않는 것은 공용 발급과 같다. 확장자는 서버가 {@code contentType} 에서 정한다.
 */
@Schema(description = "명의이전 서류 업로드 주소 발급 요청")
public record DealDocumentUploadRequest(

        @Schema(description = "파일 MIME 타입, 서류는 PDF 만 받는다", example = "application/pdf",
                allowableValues = {"application/pdf"})
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType,

        @Schema(description = "파일 크기(바이트), 최대 20MB. 실제 업로드할 파일과 정확히 같아야 합니다.",
                example = "2481920")
        @Positive(message = "contentLength는 0보다 커야 합니다.")
        @Max(value = UploadContentType.MAX_DOCUMENT_SIZE,
                message = "명의이전 서류는 20MB를 넘을 수 없습니다.")
        long contentLength
) {
}
