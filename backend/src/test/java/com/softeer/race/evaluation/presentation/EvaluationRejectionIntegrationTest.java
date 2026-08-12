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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방문 결과 반려를 컨트롤러에서 DB까지
 * <p>
 * 1. 반려
 * 상태와 사유가 함께 저장되고 <b>차량은 진단 전 그대로 남는지</b>. 반려가 차량을 건드리지 않는다는
 * 결정이 재신청의 전제라 여기서 못 박는다
 * <p>
 * 2. 사유 전달
 * 판매자가 자기 신청 상세에서 사유를 읽을 수 있는지. 이 경로가 없으면 판매자는 신청이 끝난 것만
 * 알고 왜인지는 알 수 없어, 이 기능이 풀려던 문제가 그대로 남는다
 * <p>
 * 3. 승인된 신청
 * 결과를 제출해 승인된 뒤에는 반려로 뒤집히지 않는지. 진단서 재제출은 APPROVED에도 되지만
 * 반려는 안 된다는, 두 검사가 갈라지는 지점이다
 * <p>
 * 4. 이미 반려된 신청
 * 두 번째 반려가 막히고 <b>처음 사유가 그대로 남는지</b>. 판매자가 이미 읽은 사유가 조용히
 * 바뀌지 않아야 한다
 * <p>
 * 5. 반려 자격
 * 담당이 아닌 평가사와 무관한 회원이 막히는지, 아직 아무도 수락하지 않은 신청은 누구에게도
 * 막히는지. 결과 제출과 같은 규칙을 쓰므로 한쪽만 고치면 여기서 갈린다
 * <p>
 * 6. 반려된 신청과 진단서
 * 반려로 끝난 뒤에는 결과를 제출할 수 없는지. 반대 방향의 잠금이다
 * <p>
 * 7. 사유 필수
 * 빈 사유가 400으로 막히고 <b>상태가 그대로인지</b>
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각(NOW(6))으로 심기므로
 * 앱 Clock만 옮기면 전 시나리오가 401이 된다.
 * <p>
 * 알림이 실제로 쌓이는지는 여기서 보지 않는다. 커밋 경계가 검증 대상이라 트랜잭션 없이 돌아야 하고,
 * 그 시나리오는 {@code EvaluationRejectedNotificationIntegrationTest}가 맡는다.
 */
@DisplayName("방문 결과 반려 통합 테스트")
@Transactional
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationRejectionIntegrationTest extends IntegrationTestSupport {

    /** 601(박평가)에게 배정된 진행 중 신청. 반려의 주 대상이다 */
    private static final long EVALUATION_ID = 600L;
    private static final long VEHICLE_ID = 600L;
    /** 픽스처가 이미 반려로 심어 둔 신청. 배정은 되어 있어 담당자가 아니라 상태에서 걸려야 한다 */
    private static final long REJECTED_EVALUATION_ID = 601L;
    /** 아직 아무도 수락하지 않은 신청 */
    private static final long UNASSIGNED_EVALUATION_ID = 602L;

    private static final String SELLER_TOKEN = "report-seller-token";
    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String OTHER_EVALUATOR_TOKEN = "report-eval2-token";
    private static final String STRANGER_TOKEN = "report-other-token";

    private static final String REASON = "번호판이 등록된 차량과 일치하지 않아 매물로 등록할 수 없습니다.";
    /** 픽스처가 601에 심어 둔 사유. 두 번째 반려에도 이 값이 남아야 한다 */
    private static final String FIXTURE_REASON = "차량 상태 확인 불가";

    // 테스트 설정의 aws.s3.cdn-base-url과 같아야 한다. 다르면 UNMANAGED_DOCUMENT_URL로 떨어진다
    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    @Test
    @DisplayName("시나리오 1 : 반려하면 상태와 사유가 저장되고 차량은 진단 전 그대로 남는다")
    void scenario1_StoresStatusAndReason() throws Exception {
        // when
        reject(EVALUATION_ID, EVALUATOR_TOKEN, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value(REASON))
                .andExpect(jsonPath("$.rejectedAt").exists());

        // then
        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("REJECTED");
        assertThat(evaluationColumn(EVALUATION_ID, "reject_reason")).isEqualTo(REASON);

        // 배정은 지우지 않는다. 지우면 누가 반려했는지 되짚을 수 없다
        assertThat(jdbcTemplate.queryForObject(
                "select evaluator_id from evaluation where id = ?", Long.class, EVALUATION_ID))
                .isEqualTo(601L);

        // 차량은 손대지 않는다. 이 값들이 비어 있어야 같은 번호판 재신청이 성립한다
        Map<String, Object> vehicle = jdbcTemplate.queryForMap(
                "select mileage, estimated_price, diagnostic_report_url from vehicle where id = ?",
                VEHICLE_ID);
        assertThat(vehicle.get("mileage")).isNull();
        assertThat(vehicle.get("estimated_price")).isNull();
        assertThat(vehicle.get("diagnostic_report_url")).isNull();
    }

    /**
     * 반려 사유가 판매자에게 실제로 도달하는지. 저장만 되고 내려보내는 경로가 없으면
     * 판매자는 여전히 왜 끝났는지 알 수 없다.
     */
    @Test
    @DisplayName("시나리오 2 : 판매자가 자기 신청 상세에서 반려 사유를 읽는다")
    void scenario2_SellerReadsReason() throws Exception {
        // given
        reject(EVALUATION_ID, EVALUATOR_TOKEN, REASON).andExpect(status().isOk());

        // when & then
        mockMvc.perform(get("/api/evaluations/{id}", EVALUATION_ID)
                        .cookie(sessionCookie(SELLER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value(REASON))
                // 진단 결과는 여전히 비어 있다. 그 비어 있음과 REJECTED가 함께 상황을 말한다
                .andExpect(jsonPath("$.mileage").doesNotExist())
                .andExpect(jsonPath("$.estimatedPrice").doesNotExist())
                .andExpect(jsonPath("$.diagnosticReportUrl").doesNotExist());
    }

    // 반려 전에는 필드가 아예 나가지 않아야 한다. 빈 문자열로 나가면
    // 화면이 "사유 없이 반려됨"과 "반려되지 않음"을 구분하지 못한다
    @Test
    @DisplayName("시나리오 2-1 : 반려 전 상세에는 rejectReason이 없다")
    void scenario2_1_ReasonAbsentBeforeRejection() throws Exception {
        mockMvc.perform(get("/api/evaluations/{id}", EVALUATION_ID)
                        .cookie(sessionCookie(SELLER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());
    }

    /**
     * 두 검사가 갈라지는 지점을 실물로 고정한다. 진단서는 재제출 때문에 APPROVED에도 붙지만,
     * 반려는 REQUESTED만 받는다. 한 검사로 합치는 순간 여기가 200이 되고, 이미 나간 승인 알림이
     * 거짓이 되며 그 사이 올라간 경매글이 진단 결과 없는 차량을 가리킨다.
     */
    @Test
    @DisplayName("시나리오 3 : 결과를 제출해 승인된 뒤에는 반려할 수 없다")
    void scenario3_RejectsApproved() throws Exception {
        // given : 같은 평가사가 결과를 제출해 승인으로 끝낸다
        submitResult(EVALUATION_ID, EVALUATOR_TOKEN).andExpect(status().isOk());

        // when & then
        reject(EVALUATION_ID, EVALUATOR_TOKEN, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_REJECTABLE"));

        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("APPROVED");
        assertThat(evaluationColumn(EVALUATION_ID, "reject_reason")).isNull();
    }

    @Test
    @DisplayName("시나리오 4 : 이미 반려된 신청을 다시 반려할 수 없고 처음 사유가 남는다")
    void scenario4_RejectsAlreadyRejected() throws Exception {
        reject(REJECTED_EVALUATION_ID, EVALUATOR_TOKEN, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_REJECTABLE"));

        // 사유를 고쳐 쓰는 통로가 되면 판매자가 이미 읽은 사유가 조용히 바뀐다
        assertThat(evaluationColumn(REJECTED_EVALUATION_ID, "reject_reason"))
                .isEqualTo(FIXTURE_REASON);
    }

    /** 역할은 공통 인가에서, 담당 여부와 배정 상태는 도메인에서 차례로 판정한다. */
    @Test
    @DisplayName("시나리오 5 : 담당이 아니면 403, 아직 배정 전이면 누구에게도 409")
    void scenario5_ChecksAssignment() throws Exception {
        reject(EVALUATION_ID, OTHER_EVALUATOR_TOKEN, REASON)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNED_EVALUATOR"));

        reject(EVALUATION_ID, STRANGER_TOKEN, REASON)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        for (String token : List.of(EVALUATOR_TOKEN, OTHER_EVALUATOR_TOKEN)) {
            reject(UNASSIGNED_EVALUATION_ID, token, REASON)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVALUATION_EVALUATOR_NOT_ASSIGNED"));
        }

        reject(UNASSIGNED_EVALUATION_ID, STRANGER_TOKEN, REASON)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        // 거부된 요청은 아무것도 남기지 않는다
        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("REQUESTED");
        assertThat(evaluationColumn(EVALUATION_ID, "reject_reason")).isNull();
    }

    // 시나리오 3의 반대 방향이다. 반려로 끝난 신청에 결과가 붙으면 상태와 데이터가 어긋나고,
    // 재신청으로 생긴 새 평가와 어느 쪽이 유효한지 알 수 없어진다
    @Test
    @DisplayName("시나리오 6 : 반려로 끝난 뒤에는 결과를 제출할 수 없다")
    void scenario6_RejectedBlocksSubmit() throws Exception {
        reject(EVALUATION_ID, EVALUATOR_TOKEN, REASON).andExpect(status().isOk());

        submitResult(EVALUATION_ID, EVALUATOR_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_DIAGNOSABLE"));

        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("REJECTED");
    }

    /**
     * 사유 없이 끝낼 수 있으면 판매자는 신청이 끝난 것만 알고 왜인지는 알 수 없다 — 이 기능이
     * 풀려던 문제가 그대로 남는다. 공백만 보내는 것도 같은 상황이라 {@code @NotBlank}로 막는다.
     */
    @Test
    @DisplayName("시나리오 7 : 사유가 비어 있으면 400이고 상태는 그대로다")
    void scenario7_RequiresReason() throws Exception {
        for (String blank : List.of("", "   ")) {
            reject(EVALUATION_ID, EVALUATOR_TOKEN, blank)
                    .andExpect(status().isBadRequest());
        }

        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("REQUESTED");
    }

    // 핸들러의 @LoginUser를 떼면 이 테스트만 깨진다. 인증 요구를 선언하는 곳이 그 파라미터
    // 하나뿐이라, 빠뜨리면 조용히 아무나 남의 신청을 반려할 수 있는 API가 된다
    @Test
    @DisplayName("시나리오 8 : 세션 쿠키 없이 반려하면 401이다")
    void scenario8_RequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/evaluations/{id}/rejection", EVALUATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectBody(REASON)))
                .andExpect(status().isUnauthorized());

        assertThat(evaluationColumn(EVALUATION_ID, "status")).isEqualTo("REQUESTED");
    }

    private ResultActions reject(long evaluationId, String token, String reason) throws Exception {
        return mockMvc.perform(post("/api/evaluations/{id}/rejection", evaluationId)
                .cookie(sessionCookie(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rejectBody(reason)));
    }

    // 키워드는 빈 배열로 보낸다. 필드 자체는 @NotNull 이라 뺄 수 없지만 0개는 정상이고,
    // 여기서 결과 제출은 "승인으로 끝난 상태"를 만들기 위한 준비라 키워드가 관심사가 아니다
    private ResultActions submitResult(long evaluationId, String token) throws Exception {
        return mockMvc.perform(put("/api/evaluations/{id}/result", evaluationId)
                .cookie(sessionCookie(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "mileage": 45000,
                          "estimatedPrice": 21500000,
                          "imageUrls": ["%s"],
                          "diagnosticReportUrl": "%s",
                          "keywords": []
                        }
                        """.formatted(IMAGE_URL, DOCUMENT_URL)));
    }

    private static String rejectBody(String reason) {
        return """
                {"reason": "%s"}
                """.formatted(reason);
    }

    private static Cookie sessionCookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }

    private String evaluationColumn(long evaluationId, String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from evaluation where id = ?", String.class, evaluationId);
    }
}
