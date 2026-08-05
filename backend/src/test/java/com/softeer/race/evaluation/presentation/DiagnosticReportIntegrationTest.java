package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진단서 첨부·조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 첨부
 * 진단서 행이 생기고 응답에 첨부 시각이 실리는지
 * <p>
 * 2. 교체
 * 다시 보내면 행이 늘지 않고 주소만 바뀌는지. 재첨부를 409로 막지 않기로 한 결정을 고정한다
 * <p>
 * 3. 종류 구분
 * 우리가 발급한 주소라도 이미지면 거부되는지. 사진과 문서를 갈라 둔 이유가 여기 있다
 * <p>
 * 4. 종료된 평가
 * 반려된 신청에는 못 붙이는지. REJECTED로 만드는 공개 경로가 없어 이 시나리오는 여기에만 있다
 * <p>
 * 5. 인가 없음
 * 로그인만 하면 역할·소유와 무관하게 통과하는지. <b>지금의 동작을 고정하는 테스트다</b> —
 * 인가가 들어오면 여기가 먼저 깨져야 그 변경이 의도된 것임을 확인할 수 있다
 * <p>
 * 6. 미첨부와 없는 평가
 * 아직 안 붙은 평가와 없는 평가가 서로 다른 코드로 구분되는지
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각(NOW(6))으로 심기므로
 * 앱 Clock만 옮기면 전 시나리오가 401이 된다. 진단서 흐름은 시각에 기대는 규칙이 없어
 * 고정할 이유도 없다.
 */
@DisplayName("진단서 첨부·조회 통합 테스트")
@Transactional
@Sql("/sql/diagnostic-report-fixture.sql")
class DiagnosticReportIntegrationTest extends IntegrationTestSupport {

    private static final long EVALUATION_ID = 600L;
    private static final long REJECTED_EVALUATION_ID = 601L;
    private static final long UNKNOWN_EVALUATION_ID = 699L;

    private static final String SELLER_TOKEN = "report-seller-token";
    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String STRANGER_TOKEN = "report-other-token";

    // 테스트 설정의 aws.s3.cdn-base-url과 같아야 한다. 다르면 전부 UNMANAGED_DOCUMENT_URL로 떨어진다
    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String NEW_DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    @Test
    @DisplayName("진단서를 붙이면 행이 생긴다")
    void attach() throws Exception {
        // when
        attach(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.attachedAt").exists());

        // then
        assertThat(fileUrlOf(EVALUATION_ID)).isEqualTo(DOCUMENT_URL);

        // 첨부는 배정을 대신하지 않는다. 배정은 평가사가 대기 목록에서 수락할 때 일어나는
        // 별개 흐름이고(EvaluationAssignmentService), 진단서를 올렸다고 담당이 정해지지 않는다.
        // 여기서 채워 버리면 "먼저 수락한 한 명"이라는 배정 규칙을 우회하는 통로가 생긴다
        assertThat(assignedEvaluatorOf(EVALUATION_ID)).isNull();
    }

    @Test
    @DisplayName("다시 보내면 행이 늘지 않고 주소만 바뀐다")
    void attachReplaces() throws Exception {
        // given : 스캔이 잘못돼 다시 올리는 흐름이다
        attach(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL).andExpect(status().isOk());

        // when
        attach(EVALUATION_ID, EVALUATOR_TOKEN, NEW_DOCUMENT_URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(NEW_DOCUMENT_URL));

        // then : evaluation_id에 unique 제약이 있어 행을 새로 만들면 여기서 깨진다
        assertThat(reportCountOf(EVALUATION_ID)).isEqualTo(1);
        assertThat(fileUrlOf(EVALUATION_ID)).isEqualTo(NEW_DOCUMENT_URL);
    }

    @Test
    @DisplayName("우리가 발급한 주소라도 이미지면 400")
    void attachRejectsImageUrl() throws Exception {
        // when & then : 사진과 문서를 키 접두사로 갈라 둔 덕에 구분된다
        attach(EVALUATION_ID, EVALUATOR_TOKEN, IMAGE_URL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVALUATION_UNMANAGED_DOCUMENT_URL"));

        assertThat(reportCountOf(EVALUATION_ID)).isZero();
    }

    @Test
    @DisplayName("반려되어 끝난 평가에는 409")
    void attachRejectsRejectedEvaluation() throws Exception {
        // when & then : 끝난 신청에 진단 결과가 붙으면 상태와 데이터가 어긋난다
        attach(REJECTED_EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_DIAGNOSABLE"));

        assertThat(reportCountOf(REJECTED_EVALUATION_ID)).isZero();
    }

    /**
     * 지금의 동작을 고정하는 테스트다. 평가와 아무 관계 없는 일반 회원이 진단서를 붙이고 볼 수
     * 있는 것은 <b>인가를 아직 도입하지 않았기 때문</b>이고, 바람직한 상태라서가 아니다.
     * <p>
     * 인가가 들어오면 이 테스트가 먼저 깨진다. 그때 이 메서드를 403·404를 기대하도록 고치면
     * 되고, 그 변경이 의도된 것이라는 확인을 이 테스트가 강제한다.
     */
    @Test
    @DisplayName("아직 인가가 없어 로그인만 하면 누구나 붙이고 볼 수 있다")
    void allowsAnyLoggedInUser() throws Exception {
        // when : 판매자도 평가사도 아닌 회원이다
        attach(EVALUATION_ID, STRANGER_TOKEN, DOCUMENT_URL).andExpect(status().isOk());

        // then
        find(EVALUATION_ID, STRANGER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL));
    }

    @Test
    @DisplayName("판매자도 붙어 있는 진단서를 조회할 수 있다")
    void find() throws Exception {
        // given
        attach(EVALUATION_ID, EVALUATOR_TOKEN, DOCUMENT_URL).andExpect(status().isOk());

        // when & then
        find(EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL));
    }

    @Test
    @DisplayName("아직 붙지 않았으면 평가 없음과 다른 코드로 알린다")
    void findDistinguishesMissingReportFromMissingEvaluation() throws Exception {
        // when & then : 화면이 "아직 등록 전"과 "잘못된 접근"을 구분해 안내할 수 있어야 한다
        find(EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_DIAGNOSTIC_REPORT_NOT_FOUND"));

        find(UNKNOWN_EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
    }

    private ResultActions attach(long evaluationId, String rawToken, String fileUrl) throws Exception {
        return mockMvc.perform(put(path(evaluationId))
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fileUrl": "%s"}
                        """.formatted(fileUrl)));
    }

    private ResultActions find(long evaluationId, String rawToken) throws Exception {
        return mockMvc.perform(get(path(evaluationId))
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken)));
    }

    private static String path(long evaluationId) {
        return "/api/evaluations/" + evaluationId + "/diagnostic-report";
    }

    private String fileUrlOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select file_url from diagnostic_report where evaluation_id = ?",
                String.class, evaluationId);
    }

    private int reportCountOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from diagnostic_report where evaluation_id = ?",
                Integer.class, evaluationId);
    }

    private Long assignedEvaluatorOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select evaluator_id from evaluation where id = ?", Long.class, evaluationId);
    }
}
