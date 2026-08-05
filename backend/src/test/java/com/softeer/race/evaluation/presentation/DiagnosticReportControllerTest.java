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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>조회는 200과 주소를 준다</li>
 *   <li>진단서가 없으면 404</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 * <p>
 * 첨부 시나리오가 없다. 진단서를 붙이는 것은 평가 결과 제출이라
 * {@code EvaluationResultControllerTest}가 맡는다.
 */
@WebMvcTest(controllers = DiagnosticReportController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("진단서 조회 컨트롤러")
class DiagnosticReportControllerTest {

    private static final long EVALUATION_ID = 500L;
    private static final long EVALUATOR_ID = 501L;
    private static final LocalDateTime ATTACHED_AT = LocalDateTime.of(2026, 8, 5, 15, 30);

    private static final String PATH = "/api/evaluations/" + EVALUATION_ID + "/diagnostic-report";
    private static final String DOCUMENT_URL =
            "https://cdn.race.dev/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

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
    @DisplayName("조회는 200과 주소를 준다")
    void find() throws Exception {
        // given
        given(diagnosticReportService.find(EVALUATION_ID)).willReturn(
                new DiagnosticReportInfo(EVALUATION_ID, DOCUMENT_URL, ATTACHED_AT));

        // when & then
        mockMvc.perform(get(PATH).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.attachedAt").value("2026-08-05T15:30:00"));
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

        // when & then
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        then(diagnosticReportService).shouldHaveNoInteractions();
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token");
    }
}
