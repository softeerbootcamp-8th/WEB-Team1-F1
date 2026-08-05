package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 진단서 첨부 · 조회 결과. 두 API가 같은 형태를 쓴다 — 첨부의 결과는 "지금 붙어 있는 진단서"이고
 * 그건 조회가 돌려주는 것과 같다.
 */
@Schema(description = "진단서 응답")
public record DiagnosticReportResponse(

        @Schema(description = "진단서가 붙은 평가 ID", example = "1")
        Long evaluationId,

        @Schema(description = "진단서 파일 주소. 만료되지 않는 공개 주소입니다.",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        String fileUrl,

        @Schema(description = "지금 붙어 있는 파일이 올라온 시각. 교체하면 갱신됩니다.",
                example = "2026-08-05T15:30:00")
        LocalDateTime attachedAt
) {

    public static DiagnosticReportResponse from(DiagnosticReportInfo info) {
        return new DiagnosticReportResponse(info.evaluationId(), info.fileUrl(), info.attachedAt());
    }
}
