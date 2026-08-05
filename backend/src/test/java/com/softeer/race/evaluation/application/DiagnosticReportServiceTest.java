package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 시나리오
 * <ol>
 *   <li>최초 첨부는 새 진단서를 저장한다</li>
 *   <li>이미 붙어 있으면 새로 만들지 않고 갈아 끼운다</li>
 *   <li>우리가 발급하지 않은 주소는 UNMANAGED_DOCUMENT_URL</li>
 *   <li>발급한 주소라도 이미지면 거부한다 — 문서 종류로 묻는다</li>
 *   <li>없는 평가면 NOT_FOUND</li>
 *   <li>조회는 붙어 있는 진단서를 돌려준다</li>
 *   <li>아직 첨부되지 않았으면 DIAGNOSTIC_REPORT_NOT_FOUND</li>
 * </ol>
 * <p>
 * 첨부 자격 판정(배정된 평가사인가)은 여기서 다루지 않는다. 그 규칙은 {@code Evaluation}이
 * 들고 있어 {@code EvaluationTest}가 직접 확인하고, 이 서비스가 할 일은 <b>세션에서 온 요청자를
 * 그대로 엔티티에 넘기는 것</b>뿐이라 그 사실만 고정한다.
 * <p>
 * 반려된 평가 거부(NOT_DIAGNOSABLE)도 여기서 다루지 않는다. 상태를 REJECTED로 만드는 공개
 * 경로가 없어 목으로는 재현할 수 없고, SQL로 그 상태를 심는 통합 테스트가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("진단서 첨부·조회 서비스")
class DiagnosticReportServiceTest {

    private static final long EVALUATION_ID = 500L;
    private static final long EVALUATOR_ID = 601L;

    private static final String DOCUMENT_URL =
            "https://cdn.race.dev/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String NEW_DOCUMENT_URL =
            "https://cdn.race.dev/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String IMAGE_URL =
            "https://cdn.race.dev/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private DiagnosticReportRepository diagnosticReportRepository;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private DiagnosticReportService diagnosticReportService;

    @Test
    @DisplayName("최초 첨부는 새 진단서를 저장한다")
    void attach() {
        // given
        Evaluation evaluation = mock(Evaluation.class);
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound(evaluation);
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.empty());
        given(diagnosticReportRepository.save(any(DiagnosticReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        DiagnosticReportInfo info = attach(DOCUMENT_URL);

        // then : 상태와 담당자 판정을 엔티티에 맡긴 결정을 고정한다.
        //        세션에서 온 요청자가 그대로 넘어가야 한다 — 다른 값이 가면 배정 검사가 무의미해진다
        then(evaluation).should().validateDiagnosableBy(EVALUATOR_ID);

        assertThat(info.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(info.fileUrl()).isEqualTo(DOCUMENT_URL);
    }

    @Test
    @DisplayName("이미 붙어 있으면 새로 만들지 않고 갈아 끼운다")
    void attachReplacesExisting() {
        // given : 스캔이 잘못돼 다시 올리는 흐름이다
        DiagnosticReport existing = DiagnosticReport.attach(mock(Evaluation.class), DOCUMENT_URL);
        givenManagedDocument(NEW_DOCUMENT_URL);
        givenEvaluationFound(mock(Evaluation.class));
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.of(existing));

        // when
        DiagnosticReportInfo info = attach(NEW_DOCUMENT_URL);

        // then : 한 평가에 한 부라 새 행을 만들면 unique 제약에 걸린다
        then(diagnosticReportRepository).should(never()).save(any());
        assertThat(existing.getFileUrl()).isEqualTo(NEW_DOCUMENT_URL);
        assertThat(info.fileUrl()).isEqualTo(NEW_DOCUMENT_URL);
    }

    @Test
    @DisplayName("우리가 발급하지 않은 주소면 UNMANAGED_DOCUMENT_URL")
    void attachRejectsUnmanagedUrl() {
        // given
        given(fileStorage.isManagedUrl("https://evil.example.com/x.pdf", FileCategory.DOCUMENT))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> attach("https://evil.example.com/x.pdf"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL));

        // 거부될 요청에 조회를 태우지 않는다
        then(evaluationRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("발급한 주소라도 이미지면 거부한다")
    void attachRejectsImageUrl() {
        // given : 종류를 DOCUMENT로 묻지 않으면 차량 사진이 진단서 자리에 박힌다
        given(fileStorage.isManagedUrl(IMAGE_URL, FileCategory.DOCUMENT)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> attach(IMAGE_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL));
    }

    @Test
    @DisplayName("없는 평가면 NOT_FOUND")
    void attachRejectsUnknownEvaluation() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        given(evaluationRepository.findById(EVALUATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> attach(DOCUMENT_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("붙어 있는 진단서를 돌려준다")
    void find() {
        // given
        given(evaluationRepository.existsById(EVALUATION_ID)).willReturn(true);
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.of(
                        DiagnosticReport.attach(mock(Evaluation.class), DOCUMENT_URL)));

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
    @DisplayName("아직 첨부되지 않았으면 DIAGNOSTIC_REPORT_NOT_FOUND")
    void findRejectsMissingReport() {
        // given : 평가는 있고 진단서만 없는 경우다. 평가 자체를 못 찾는 것과 구분돼야
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

    private void givenManagedDocument(String fileUrl) {
        given(fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).willReturn(true);
    }

    private void givenEvaluationFound(Evaluation evaluation) {
        given(evaluationRepository.findById(EVALUATION_ID)).willReturn(Optional.of(evaluation));
    }

    private DiagnosticReportInfo attach(String fileUrl) {
        return diagnosticReportService.attach(EVALUATION_ID, EVALUATOR_ID, fileUrl);
    }
}
