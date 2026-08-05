package com.softeer.race.evaluation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 진단서 첨부 요청. 업로드 주소 발급 API가 돌려준 {@code fileUrl}을 그대로 보낸다.
 * <p>
 * 형식이 PDF인지 여기서 보지 않는다. 확장자를 정규식으로 걸어 봐야 <b>주소가 우리 것인지</b>는
 * 알 수 없고, 그 판정은 어차피 저장소가 발급 규칙으로 한다. 두 곳에서 형식을 보면 발급 형식이
 * 늘 때 한쪽만 고쳐 어긋난다.
 */
@Schema(description = "진단서 첨부 요청")
public record DiagnosticReportAttachRequest(

        @Schema(description = "업로드를 마친 진단서 주소. documents/ 아래로 발급된 주소여야 합니다.",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        @NotBlank(message = "진단서 주소는 필수입니다.")
        String fileUrl
) {
}
