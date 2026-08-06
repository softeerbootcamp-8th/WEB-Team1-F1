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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진단서 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 조회 권한
 * 판매자와 담당 평가사만 열리는지. 돌려주는 주소가 공개라 이 검사가 기밀을 보장하지는 못하지만,
 * <b>출품 전에는 아직 공개된 자료가 아니고</b> 한 번 나간 주소는 회수할 수 없다.
 * 입찰자에게 보여주는 일은 이 API가 하지 않는다 — 입찰자는 evaluationId를 알 수 없고,
 * 경매방 응답이 주소를 담는 형태가 된다
 * <p>
 * 2. 미제출과 없는 평가
 * 아직 결과가 안 온 평가와 없는 평가가 서로 다른 코드로 구분되는지
 * <p>
 * 진단서를 붙이는 시나리오는 여기 없다. 붙이는 입구가 평가 결과 제출 하나뿐이라
 * {@code EvaluationResultIntegrationTest}가 맡는다.
 */
@DisplayName("진단서 조회 통합 테스트")
@Transactional
@Sql("/sql/diagnostic-report-fixture.sql")
class DiagnosticReportIntegrationTest extends IntegrationTestSupport {

    private static final long EVALUATION_ID = 600L;
    private static final long UNKNOWN_EVALUATION_ID = 699L;

    private static final String SELLER_TOKEN = "report-seller-token";
    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String STRANGER_TOKEN = "report-other-token";

    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    @Test
    @DisplayName("판매자와 담당 평가사만 조회할 수 있다")
    void findChecksViewer() throws Exception {
        // given : 담당 평가사가 결과를 제출해 둔다
        submitResult().andExpect(status().isOk());

        // when & then
        find(EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUrl").value(DOCUMENT_URL));

        find(EVALUATION_ID, EVALUATOR_TOKEN).andExpect(status().isOk());

        // 무관한 회원에게는 존재 여부까지 감춘다. 돌려주는 주소가 공개라 한 번 나가면
        // 회수할 수 없고, 403으로 구분해 주면 id를 훑어 어느 평가에 진단서가 붙었는지 알아낼 수 있다
        find(EVALUATION_ID, STRANGER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("아직 결과가 안 왔으면 평가 없음과 다른 코드로 알린다")
    void findDistinguishesMissingReportFromMissingEvaluation() throws Exception {
        // when & then : 화면이 "아직 진단 전"과 "잘못된 접근"을 구분해 안내할 수 있어야 한다
        find(EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_DIAGNOSTIC_REPORT_NOT_FOUND"));

        find(UNKNOWN_EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
    }

    private ResultActions submitResult() throws Exception {
        return mockMvc.perform(put("/api/evaluations/" + EVALUATION_ID + "/result")
                .cookie(cookie(EVALUATOR_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "mileage": 45000,
                          "estimatedPrice": 21500000,
                          "imageUrls": ["%s"],
                          "diagnosticReportUrl": "%s"
                        }
                        """.formatted(IMAGE_URL, DOCUMENT_URL)));
    }

    private ResultActions find(long evaluationId, String rawToken) throws Exception {
        return mockMvc.perform(get("/api/evaluations/" + evaluationId + "/diagnostic-report")
                .cookie(cookie(rawToken)));
    }

    private static Cookie cookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }
}
