package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.evaluation.application.EvaluationResultService;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultPatchCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.domain.VehicleKeyword;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>한 항목만 보내면 나머지는 커맨드에서 null로 넘어간다</li>
 *   <li>보낸 항목만 커맨드에 실리고, 평가 ID는 경로에서 요청자는 세션에서 온다</li>
 *   <li>키워드 빈 배열과 필드 없음이 갈린다</li>
 *   <li>사진 빈 배열은 400 — 대표 이미지가 될 사진이 없어진다</li>
 *   <li>바꿀 항목을 하나도 안 보내면 400</li>
 *   <li>범위를 벗어난 값은 제출과 똑같이 400</li>
 *   <li>결과가 제출되지 않았으면 409</li>
 *   <li>세션이 없으면 401이고 서비스까지 가지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = EvaluationResultController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("평가 결과 항목별 수정 컨트롤러")
class EvaluationResultPatchControllerTest {

    private static final long EVALUATION_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long VEHICLE_ID = 6000L;
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 8, 5, 15, 30);

    private static final String PATH = "/api/evaluations/" + EVALUATION_ID + "/result";
    private static final String IMAGE_URL = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String NEW_IMAGE_URL = "https://cdn.race.dev/images/2026/08/b.jpg";
    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";

    private static final String MILEAGE_ONLY = """
            {
              "mileage": 46000
            }
            """;

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
    @DisplayName("한 항목만 보내면 200과 수정 뒤의 결과 전부를 준다")
    void patchesGivenField() throws Exception {
        // given
        givenPatchReturnsInfo();

        // when & then : 응답은 바꾼 것만이 아니라 결과 전부다. 그래야 판매자 화면을 그대로 다시 그린다
        request(MILEAGE_ONLY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID))
                .andExpect(jsonPath("$.mileage").value(46000))
                .andExpect(jsonPath("$.estimatedPrice").value(21500000))
                .andExpect(jsonPath("$.imageUrls[0]").value(IMAGE_URL))
                .andExpect(jsonPath("$.diagnosticReportUrl").value(DOCUMENT_URL));
    }

    @Test
    @DisplayName("보내지 않은 항목은 커맨드에서 null이 된다")
    void patchCarriesOnlyGivenFields() throws Exception {
        // given
        givenPatchReturnsInfo();

        // when
        request(MILEAGE_ONLY);

        // then : 여기서 0이나 빈 목록이 들어가면 사진만 바꾸려던 요청이 주행거리를 0으로 덮는다
        EvaluationResultPatchCommand command = captureCommand();

        assertThat(command.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(command.evaluatorId()).isEqualTo(EVALUATOR_ID);
        assertThat(command.mileage()).isEqualTo(46_000);
        assertThat(command.estimatedPrice()).isNull();
        assertThat(command.imageUrls()).isNull();
        assertThat(command.diagnosticReportUrl()).isNull();
        assertThat(command.keywords()).isNull();
    }

    @Test
    @DisplayName("사진 목록은 순서 그대로 커맨드에 실린다")
    void patchCarriesImageOrder() throws Exception {
        // given : 낱장을 더하고 순서를 바꾸는 흐름이 이 배열 하나로 표현된다
        givenPatchReturnsInfo();

        // when
        request("""
                {
                  "imageUrls": ["%s", "%s"]
                }
                """.formatted(NEW_IMAGE_URL, IMAGE_URL));

        // then
        assertThat(captureCommand().imageUrls()).containsExactly(NEW_IMAGE_URL, IMAGE_URL);
    }

    @Test
    @DisplayName("키워드 빈 배열은 전부 지우라는 뜻이라 그대로 넘어간다")
    void patchCarriesEmptyKeywords() throws Exception {
        // given : 제출과 달리 빈 배열과 필드 없음이 여기서는 갈린다
        givenPatchReturnsInfo();

        // when
        request("""
                {
                  "keywords": []
                }
                """);

        // then : null로 뭉개면 평가사가 뺀 키워드가 그대로 남는다
        assertThat(captureCommand().keywords()).isEmpty();
    }

    @Test
    @DisplayName("바꿀 항목을 하나도 보내지 않으면 400")
    void patchRejectsEmptyBody() throws Exception {
        // given : 200 no-op으로 두면 필드 이름을 틀린 요청이 성공으로 보인다
        request("{}")
                .andExpect(status().isBadRequest());

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사진을 빈 배열로 보내면 400")
    void patchRejectsEmptyImages() throws Exception {
        // given : 0장이 되면 Vehicle.mainPhotoUrl에 넣을 값이 없어진다
        request("""
                {
                  "imageUrls": []
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrls"));

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주행거리가 0 이하면 제출과 똑같이 400")
    void patchRejectsNonPositiveMileage() throws Exception {
        // given : 값의 범위는 제출과 같아야 한다. 다르면 제출은 막히는 값이 수정으로 들어간다
        request("""
                {
                  "mileage": 0
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mileage"));

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("결과가 제출되지 않은 평가면 409")
    void patchRejectsUnsubmittedResult() throws Exception {
        // given : 400이 아니다. 요청은 올바르고 거부되는 이유는 서버가 든 상태뿐이다
        willThrow(new BusinessException(EvaluationErrorCode.RESULT_NOT_SUBMITTED))
                .given(evaluationResultService).patch(any(EvaluationResultPatchCommand.class));

        // when & then
        request(MILEAGE_ONLY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_RESULT_NOT_SUBMITTED"));
    }

    @Test
    @DisplayName("다른 평가사가 담당이면 403")
    void patchRejectsOtherEvaluator() throws Exception {
        // given
        willThrow(new BusinessException(EvaluationErrorCode.NOT_ASSIGNED_EVALUATOR))
                .given(evaluationResultService).patch(any(EvaluationResultPatchCommand.class));

        // when & then
        request(MILEAGE_ONLY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void requiresLogin() throws Exception {
        // given
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        // when & then : 인터셉터가 막으므로 본문 파싱 전에 끝난다
        mockMvc.perform(patch(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MILEAGE_ONLY))
                .andExpect(status().isUnauthorized());

        then(evaluationResultService).shouldHaveNoInteractions();
    }

    private void givenPatchReturnsInfo() {
        given(evaluationResultService.patch(any(EvaluationResultPatchCommand.class)))
                .willReturn(new EvaluationResultInfo(
                        EVALUATION_ID, VEHICLE_ID, "APPROVED", 46_000, 21_500_000L,
                        List.of(IMAGE_URL), DOCUMENT_URL, SUBMITTED_AT,
                        List.of(VehicleKeyword.ACCIDENT_FREE)));
    }

    private EvaluationResultPatchCommand captureCommand() {
        ArgumentCaptor<EvaluationResultPatchCommand> captor =
                ArgumentCaptor.forClass(EvaluationResultPatchCommand.class);
        then(evaluationResultService).should().patch(captor.capture());

        return captor.getValue();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(patch(PATH)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
