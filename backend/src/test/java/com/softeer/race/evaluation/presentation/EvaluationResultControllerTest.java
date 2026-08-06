package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.evaluation.application.EvaluationResultService;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>제출은 200과 반영된 결과를 준다</li>
 *   <li>평가 ID는 경로에서, 요청자는 세션에서, 나머지는 본문에서 온다</li>
 *   <li>주행거리·시세가 0 이하면 400</li>
 *   <li>사진이 없으면 400</li>
 *   <li>다른 평가사가 담당이면 403</li>
 *   <li>아직 담당자가 없으면 409</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = EvaluationResultController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("평가 결과 제출 컨트롤러")
class EvaluationResultControllerTest {

    private static final long EVALUATION_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long VEHICLE_ID = 6000L;
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 8, 5, 15, 30);

    private static final String PATH = "/api/evaluations/" + EVALUATION_ID + "/result";
    private static final String IMAGE_URL = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";

    private static final String BODY = """
            {
              "mileage": 45000,
              "estimatedPrice": 21500000,
              "imageUrls": ["%s"],
              "diagnosticReportUrl": "%s"
            }
            """.formatted(IMAGE_URL, DOCUMENT_URL);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationResultService evaluationResultService;

    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(EVALUATOR_ID));
    }

    @Test
    @DisplayName("제출은 200과 반영된 결과를 준다")
    void submit() throws Exception {
        // given : POST·201이 아니다. 재제출이 교체라 같은 요청을 몇 번 보내도 결과가 같다
        givenSubmitReturnsInfo();

        // when & then
        request(BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.mileage").value(45000))
                .andExpect(jsonPath("$.estimatedPrice").value(21500000))
                .andExpect(jsonPath("$.imageUrls[0]").value(IMAGE_URL))
                .andExpect(jsonPath("$.diagnosticReportUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.submittedAt").value("2026-08-05T15:30:00"));
    }

    @Test
    @DisplayName("평가 ID는 경로에서, 요청자는 세션에서, 나머지는 본문에서 온다")
    void submitCarriesInputs() throws Exception {
        // given
        givenSubmitReturnsInfo();

        // when
        request(BODY);

        // then : 요청자가 본문에서 오면 남의 이름을 대고 제출할 수 있어
        //        "배정된 평가사만"이라는 규칙이 무의미해진다
        EvaluationResultSubmitCommand command = captureCommand();

        assertThat(command.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(command.evaluatorId()).isEqualTo(EVALUATOR_ID);
        assertThat(command.mileage()).isEqualTo(45_000);
        assertThat(command.estimatedPrice()).isEqualTo(21_500_000L);
        assertThat(command.imageUrls()).containsExactly(IMAGE_URL);
        assertThat(command.diagnosticReportUrl()).isEqualTo(DOCUMENT_URL);
    }

    @Test
    @DisplayName("주행거리가 0 이하면 400")
    void submitRejectsNonPositiveMileage() throws Exception {
        request("""
                {
                  "mileage": 0,
                  "estimatedPrice": 21500000,
                  "imageUrls": ["%s"],
                  "diagnosticReportUrl": "%s"
                }
                """.formatted(IMAGE_URL, DOCUMENT_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mileage"));

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사진이 한 장도 없으면 400")
    void submitRejectsEmptyImages() throws Exception {
        // given : 진단 결과에 사진이 없으면 경매글 썸네일을 만들 수 없다
        request("""
                {
                  "mileage": 45000,
                  "estimatedPrice": 21500000,
                  "imageUrls": [],
                  "diagnosticReportUrl": "%s"
                }
                """.formatted(DOCUMENT_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrls"));
    }

    @Test
    @DisplayName("다른 평가사가 담당이면 403")
    void submitRejectsOtherEvaluator() throws Exception {
        // given
        willThrow(new BusinessException(EvaluationErrorCode.NOT_ASSIGNED_EVALUATOR))
                .given(evaluationResultService).submit(any(EvaluationResultSubmitCommand.class));

        // when & then
        request(BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));
    }

    @Test
    @DisplayName("아직 담당자가 없으면 403이 아니라 409")
    void submitRejectsUnassignedEvaluation() throws Exception {
        // given : 권한이 모자란 것이 아니라 담당자를 정하는 단계를 지나지 않았다
        willThrow(new BusinessException(EvaluationErrorCode.EVALUATOR_NOT_ASSIGNED))
                .given(evaluationResultService).submit(any(EvaluationResultSubmitCommand.class));

        // when & then
        request(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_EVALUATOR_NOT_ASSIGNED"));
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

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    private void givenSubmitReturnsInfo() {
        given(evaluationResultService.submit(any(EvaluationResultSubmitCommand.class)))
                .willReturn(new EvaluationResultInfo(
                        EVALUATION_ID, VEHICLE_ID, "APPROVED", 45_000, 21_500_000L,
                        List.of(IMAGE_URL), DOCUMENT_URL, SUBMITTED_AT));
    }

    private EvaluationResultSubmitCommand captureCommand() {
        ArgumentCaptor<EvaluationResultSubmitCommand> captor =
                ArgumentCaptor.forClass(EvaluationResultSubmitCommand.class);
        then(evaluationResultService).should().submit(captor.capture());

        return captor.getValue();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(put(PATH)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
