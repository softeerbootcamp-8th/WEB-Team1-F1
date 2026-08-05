package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.DiagnosticReport;
import java.time.LocalDateTime;

/**
 * 서비스 계층 반환값. 엔티티를 웹 계층에 노출하지 않기 위해 트랜잭션 안에서 변환한다.
 *
 * @param attachedAt {@code createdAt}이 아니라 {@code updatedAt}이다. 진단서는 교체될 수 있고,
 *                   화면이 알아야 하는 것은 <b>지금 붙어 있는 파일이 올라온 시각</b>이다.
 *                   createdAt을 쓰면 교체한 뒤에도 첫 첨부 시각이 남아 낡은 날짜를 보여준다
 */
public record DiagnosticReportInfo(
        Long evaluationId,
        String fileUrl,
        LocalDateTime attachedAt
) {

    public static DiagnosticReportInfo from(Long evaluationId, DiagnosticReport report) {
        return new DiagnosticReportInfo(evaluationId, report.getFileUrl(), report.getUpdatedAt());
    }
}
