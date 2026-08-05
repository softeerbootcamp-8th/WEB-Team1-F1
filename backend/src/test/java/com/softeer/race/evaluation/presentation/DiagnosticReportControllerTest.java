package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.evaluation.application.DiagnosticReportService;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>첨부는 200과 붙은 진단서를 준다</li>
 *   <li>평가 ID는 경로에서, 요청자는 세션에서, 주소는 본문에서 온다</li>
 *   <li>주소가 비면 400</li>
 *   <li>반려된 평가면 409</li>
 *   <li>다른 평가사가 담당이면 403</li>
 *   <li>아직 담당자가 없으면 409</li>
 *   <li>조회는 200과 주소를 준다</li>
 *   <li>진단서가 없으면 404</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 * <p>
 * 자격 판정 자체는 여기서 확인하지 않는다. 그 규칙은 {@code Evaluation}에 있고, 여기서 보는 것은
 * 각 결과가 어떤 상태 코드로 번역되는지와 요청자가 세션에서 오는지다.
 */
@WebMvcTest(controllers = DiagnosticReportController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("진단서 컨트롤러")
class DiagnosticReportControllerTest {

    private static final long EVALUATION_ID = 500L;
    private static final long EVALUATOR_ID = 501L;
    private static final LocalDateTime ATTACHED_AT = LocalDateTime.of(2026, 8, 5, 15, 30);

    private static final String PATH = "/api/evaluations/" + EVALUATION_ID + "/diagnostic-report";
    private static final String DOCUMENT_URL =
            "https://cdn.race.dev/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String BODY = """
            {"fileUrl": "%s"}
            """.formatted(DOCUMENT_URL);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosticReportService diagnosticReportService;

    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(EVALUATOR_ID));
    }

    @Test
    @DisplayName("첨부는 200과 붙은 진단서를 준다")
    void attach() throws Exception {
        // given : POST·201이 아니다. 재첨부가 교체라 같은 요청을 몇 번 보내도 결과가 같다
        given(diagnosticReportService.attach(anyLong(), anyLong(), anyString()))
                .willReturn(new DiagnosticReportInfo(EVALUATION_ID, DOCUMENT_URL, ATTACHED_AT));

        // when & then
        attachRequest(BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.attachedAt").value("2026-08-05T15:30:00"));
    }

    @Test
    @DisplayName("평가 ID는 경로에서, 요청자는 세션에서, 주소는 본문에서 온다")
    void attachCarriesIds() throws Exception {
        // given
        given(diagnosticReportService.attach(anyLong(), anyLong(), anyString()))
                .willReturn(new DiagnosticReportInfo(EVALUATION_ID, DOCUMENT_URL, ATTACHED_AT));

        // when
        attachRequest(BODY);

        // then : 요청자가 본문에서 오면 남의 이름을 대고 올릴 수 있어
        //        "배정된 평가사만"이라는 규칙이 무의미해진다
        then(diagnosticReportService).should().attach(EVALUATION_ID, EVALUATOR_ID, DOCUMENT_URL);
    }

    @Test
    @DisplayName("주소가 비면 400이고 서비스까지 가지 않는다")
    void attachRejectsBlankUrl() throws Exception {
        attachRequest("""
                {"fileUrl": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fileUrl"));

        then(diagnosticReportService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("반려된 평가면 409")
    void attachRejectsEndedEvaluation() throws Exception {
        // given : 400이 아니라 409다. 요청은 올바르고 서버 상태만이 거부 사유다
        willThrow(new BusinessException(EvaluationErrorCode.NOT_DIAGNOSABLE))
                .given(diagnosticReportService).attach(anyLong(), anyLong(), anyString());

        // when & then
        attachRequest(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_DIAGNOSABLE"));
    }

    @Test
    @DisplayName("다른 평가사가 담당이면 403")
    void attachRejectsOtherEvaluator() throws Exception {
        // given
        willThrow(new BusinessException(EvaluationErrorCode.NOT_ASSIGNED_EVALUATOR))
                .given(diagnosticReportService).attach(anyLong(), anyLong(), anyString());

        // when & then
        attachRequest(BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));
    }

    @Test
    @DisplayName("아직 담당자가 없으면 403이 아니라 409")
    void attachRejectsUnassignedEvaluation() throws Exception {
        // given : 권한이 모자란 것이 아니라 담당자를 정하는 단계를 지나지 않았다.
        //         요청자가 누구든 답이 같아 403으로 낼 근거가 없다
        willThrow(new BusinessException(EvaluationErrorCode.EVALUATOR_NOT_ASSIGNED))
                .given(diagnosticReportService).attach(anyLong(), anyLong(), anyString());

        // when & then
        attachRequest(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_EVALUATOR_NOT_ASSIGNED"));
    }

    @Test
    @DisplayName("조회는 200과 주소를 준다")
    void find() throws Exception {
        // given
        given(diagnosticReportService.find(EVALUATION_ID))
                .willReturn(new DiagnosticReportInfo(EVALUATION_ID, DOCUMENT_URL, ATTACHED_AT));

        // when & then
        mockMvc.perform(get(PATH).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL));
    }

    @Test
    @DisplayName("진단서가 아직 없으면 404")
    void findRejectsMissingReport() throws Exception {
        // given
        willThrow(new BusinessException(EvaluationErrorCode.DIAGNOSTIC_REPORT_NOT_FOUND))
                .given(diagnosticReportService).find(anyLong());

        // when & then : 평가를 못 찾는 것과 구분돼야 화면이 "아직 등록 전"을 안내할 수 있다
        mockMvc.perform(get(PATH).cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_DIAGNOSTIC_REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void requiresLogin() throws Exception {
        // given
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        // when & then : 인터셉터가 막으므로 본문 파싱 전에 끝난다
        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        then(diagnosticReportService).shouldHaveNoInteractions();
    }

    private ResultActions attachRequest(String body) throws Exception {
        return mockMvc.perform(put(PATH)
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token");
    }
}
