package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * 시나리오
 * <ol>
 *   <li>붙어 있는 진단서를 돌려준다</li>
 *   <li>없는 평가면 NOT_FOUND</li>
 *   <li>아직 제출되지 않았으면 DIAGNOSTIC_REPORT_NOT_FOUND</li>
 * </ol>
 * <p>
 * 첨부 시나리오가 없다. 진단서를 붙이는 일은 평가 결과 제출이 하고
 * ({@code EvaluationResultServiceTest}), 이 서비스는 조회만 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("진단서 조회 서비스")
class DiagnosticReportServiceTest {

    private static final long EVALUATION_ID = 500L;

    private static final String DOCUMENT_URL =
            "https://cdn.race.dev/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private DiagnosticReportRepository diagnosticReportRepository;

    @InjectMocks
    private DiagnosticReportService diagnosticReportService;

    @Test
    @DisplayName("붙어 있는 진단서를 돌려준다")
    void find() {
        // given
        given(evaluationRepository.existsById(EVALUATION_ID)).willReturn(true);
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.of(DiagnosticReport.attach(mock(Evaluation.class), DOCUMENT_URL)));

        // when
        DiagnosticReportInfo info = diagnosticReportService.find(EVALUATION_ID);

        // then
        assertThat(info.fileUrl()).isEqualTo(DOCUMENT_URL);
    }

    @Test
    @DisplayName("없는 평가를 조회하면 NOT_FOUND")
    void findRejectsUnknownEvaluation() {
        // given
        given(evaluationRepository.existsById(EVALUATION_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> diagnosticReportService.find(EVALUATION_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));

        then(diagnosticReportRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("아직 제출되지 않았으면 DIAGNOSTIC_REPORT_NOT_FOUND")
    void findRejectsMissingReport() {
        // given : 평가는 있고 결과만 없는 경우다. 평가 자체를 못 찾는 것과 구분돼야
        //         화면이 "아직 등록 전"을 안내할 수 있다
        given(evaluationRepository.existsById(EVALUATION_ID)).willReturn(true);
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> diagnosticReportService.find(EVALUATION_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.DIAGNOSTIC_REPORT_NOT_FOUND));
    }
}
