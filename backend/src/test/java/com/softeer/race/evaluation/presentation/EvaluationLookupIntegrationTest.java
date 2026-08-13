package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
 * 방문견적 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 내 것만
 * 목록에 남의 신청이 섞이지 않는지. 조회 조건이 곧 인가라 이 필터가 뚫리면 인가가 없는 것과 같다
 * <p>
 * 2. 정렬
 * 판매자 목록은 최신 접수부터, 평가사 목록은 방문일이 임박한 순인지
 * <p>
 * 3. 담당만
 * 평가사 목록에 배정받지 않은 신청이 들어오지 않는지
 * <p>
 * 4. 상세 · 진단 전후
 * 제출 전에는 결과 칸이 비어 있고 제출 뒤에 채워지는지. <b>진단 전 차량은 주행거리가 실제로
 * null이라</b> 상세가 원시 타입으로 받으면 여기서 언박싱으로 터진다
 * <p>
 * 5. 상세 권한
 * 판매자와 배정 평가사만 열리고 나머지는 404인지. 방문 주소가 들어 있어 진단서 조회처럼
 * 열어 둘 수 없다
 * <p>
 * 6. 경로 충돌
 * {@code /assignable}이 {@code /{evaluationId}}에 먹히지 않는지. 두 컨트롤러가 같은 접두사를 쓴다
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각으로 심기므로 앱 Clock만
 * 옮기면 전 시나리오가 401이 된다.
 */
@DisplayName("방문견적 조회 통합 테스트")
@Transactional
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationLookupIntegrationTest extends IntegrationTestSupport {

    /** 601에게 배정된 진행 중 신청 */
    private static final long EVALUATION_ID = 600L;
    private static final long REJECTED_EVALUATION_ID = 601L;
    private static final long UNASSIGNED_EVALUATION_ID = 602L;
    private static final long OTHER_SELLER_EVALUATION_ID = 604L;
    private static final long UNKNOWN_EVALUATION_ID = 699L;

    private static final String SELLER_TOKEN = "report-seller-token";
    private static final String OTHER_SELLER_TOKEN = "report-seller2-token";
    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String OTHER_EVALUATOR_TOKEN = "report-eval2-token";
    private static final String STRANGER_TOKEN = "report-other-token";

    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";


    // 세션만 Redis 에 살아 @Sql 이 함께 심지 못한다, 짝이 되는 세션을 여기서 심는다
    @BeforeEach
    void seedSessions() {
        SessionFixture.diagnosticReport(sessions);
    }

    @Test
    @DisplayName("내 신청 목록에 남의 신청이 섞이지 않고 최신 접수부터 나온다")
    void findMyRequests() throws Exception {
        // when & then : 600이 낸 것은 600 · 601 · 602 세 건이고 604는 다른 판매자 것이다
        lookup("/my-requests", SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.evaluations[0].evaluationId").value(UNASSIGNED_EVALUATION_ID))
                .andExpect(jsonPath("$.evaluations[2].evaluationId").value(EVALUATION_ID));

        // 다른 판매자에게는 자기 것 하나만 보인다. 아직 아무도 수락하지 않아 assigned가 false다
        lookup("/my-requests", OTHER_SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(1))
                .andExpect(jsonPath("$.evaluations[0].evaluationId")
                        .value(OTHER_SELLER_EVALUATION_ID))
                .andExpect(jsonPath("$.evaluations[0].assigned").value(false));
    }

    /**
     * 배정은 상태를 바꾸지 않는다(둘이 다른 축이라는 dev의 설계). 그래서 판매자 화면에서
     * 접수 직후와 평가사가 정해진 뒤를 가르는 것은 {@code assigned} 하나뿐이다.
     */
    @Test
    @DisplayName("배정 여부가 목록에서 구분된다")
    void findMyRequestsShowsAssignment() throws Exception {
        lookup("/my-requests", SELLER_TOKEN)
                .andExpect(status().isOk())
                // 602(미배정)가 먼저, 600(601에게 배정됨)이 마지막이다
                .andExpect(jsonPath("$.evaluations[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.evaluations[0].assigned").value(false))
                .andExpect(jsonPath("$.evaluations[2].status").value("REQUESTED"))
                .andExpect(jsonPath("$.evaluations[2].assigned").value(true));
    }

    @Test
    @DisplayName("내 담당 목록에는 배정받은 것만 나온다")
    void findMyAssignments() throws Exception {
        // when & then : 601은 600 · 601을 맡았고 602는 아무도 수락하지 않은 신청이다
        lookup("/my-assignments", EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(2))
                .andExpect(jsonPath("$.evaluations[0].assigned").value(true));

        // 수락한 적 없는 평가사에게는 빈 배열이다. 배정 대기 목록은 다른 API가 준다
        lookup("/my-assignments", OTHER_EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(0));
    }

    @Test
    @DisplayName("판매자와 평가사 목록에 차량의 최신 경매 상태가 나온다")
    void listsLatestAuctionStatus() throws Exception {
        // given : 같은 차량의 앞선 유찰 뒤 새 경매가 진행 중이다
        registerAuction(650L, "FAILED");
        registerAuction(651L, "IN_PROGRESS");

        lookup("/my-requests", SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations[2].evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.evaluations[2].auctionStatus").value("IN_PROGRESS"));

        lookup("/my-assignments", EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations[0].evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.evaluations[0].auctionStatus").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("경매 이력이 없는 차량은 목록에 경매 상태가 없다")
    void listOmitsAuctionStatusWithoutAuction() throws Exception {
        lookup("/my-requests", SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations[0].auctionStatus").doesNotExist());
    }

    @Test
    @DisplayName("진단 전 상세는 결과 칸이 비어 있고 제출 뒤에 채워진다")
    void findDetail() throws Exception {
        // given : 아직 결과가 제출되지 않았다
        lookup("/" + EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.visitAddress").value("서울 성동구 왕십리로 83"))
                .andExpect(jsonPath("$.mileage").doesNotExist())
                .andExpect(jsonPath("$.estimatedPrice").doesNotExist())
                .andExpect(jsonPath("$.diagnosticReportUrl").doesNotExist())
                // 판매 신청이 넣어 둔 카탈로그 이미지는 이 시점에도 있다
                .andExpect(jsonPath("$.imageUrls.length()").value(1))
                // 담당 평가사와 연락처는 진단 전에도 나간다. 방문 전에 연락해야 하는 값이다
                .andExpect(jsonPath("$.evaluatorName").value("박평가"))
                .andExpect(jsonPath("$.contactPhone").value("01012345678"))
                // 진단 전에는 null 이 아니라 빈 배열이다. 진단을 마쳐도 0개일 수 있어
                // null 로는 "아직 안 왔다"와 "매길 게 없었다"가 구분되지 않는다
                .andExpect(jsonPath("$.keywords").isEmpty());

        // when : 담당 평가사가 결과를 제출한다
        submitResult().andExpect(status().isOk());

        // then : 같은 엔드포인트가 이제 결과까지 준다. 이 전환이 보여야 판매자가 출품으로 넘어간다
        lookup("/" + EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.mileage").value(45000))
                .andExpect(jsonPath("$.estimatedPrice").value(21500000))
                .andExpect(jsonPath("$.imageUrls[0]").value(IMAGE_URL))
                .andExpect(jsonPath("$.diagnosticReportUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.keywords[0]").value("ACCIDENT_FREE"))
                .andExpect(jsonPath("$.keywords[1]").value("NO_LEAK"));
    }

    @Test
    @DisplayName("상세는 판매자와 배정 평가사만 열 수 있다")
    void findDetailChecksViewer() throws Exception {
        // 판매자와 담당 평가사는 통과
        lookup("/" + EVALUATION_ID, SELLER_TOKEN).andExpect(status().isOk());
        lookup("/" + EVALUATION_ID, EVALUATOR_TOKEN).andExpect(status().isOk());

        // 담당이 아닌 평가사와 무관한 회원은 존재 여부까지 감춘다.
        // 403으로 구분해 주면 id를 훑어 남의 신청과 그 방문 주소를 알아낼 수 있다
        lookup("/" + EVALUATION_ID, OTHER_EVALUATOR_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));

        lookup("/" + EVALUATION_ID, STRANGER_TOKEN).andExpect(status().isNotFound());

        // 없는 신청도 같은 코드다
        lookup("/" + UNKNOWN_EVALUATION_ID, SELLER_TOKEN).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("아직 아무도 수락하지 않은 신청은 담당자가 비어 나간다")
    void findDetailBeforeAssignment() throws Exception {
        // 602는 배정 전이다. left join fetch가 아니라 inner join이었다면
        // 이 신청이 조회 결과에서 통째로 사라져 판매자가 상세를 열 수 없다
        lookup("/" + UNASSIGNED_EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatorName").doesNotExist());
    }

    @Test
    @DisplayName("반려된 신청도 목록과 상세에 그대로 남는다")
    void findRejectedEvaluation() throws Exception {
        // 진행 중인 것만 보여주면 판매자가 왜 진행되지 않는지 알 수 없다
        lookup("/" + REJECTED_EVALUATION_ID, SELLER_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    /**
     * {@code /assignable}은 배정 컨트롤러가, {@code /{evaluationId}}는 조회 컨트롤러가 잡는다.
     * 스프링은 리터럴 경로를 패턴보다 먼저 맞추지만, 그 규칙에 기대고 있다는 사실을 고정해 둔다 —
     * 깨지면 배정 대기 목록이 "assignable이라는 이름의 신청"을 찾다가 400으로 떨어진다.
     */
    @Test
    @DisplayName("assignable 경로가 상세 조회에 먹히지 않는다")
    void assignablePathIsNotSwallowed() throws Exception {
        lookup("/assignable", EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations").isArray());
    }

    private ResultActions lookup(String path, String rawToken) throws Exception {
        return mockMvc.perform(get("/api/evaluations" + path).cookie(cookie(rawToken)));
    }

    private void registerAuction(long auctionId, String auctionStatus) {
        jdbcTemplate.update("""
                insert into auction_post
                    (id, vehicle_id, published_at, created_at, updated_at)
                values (?, ?, NOW(6), NOW(6), NOW(6))
                """, auctionId, EVALUATION_ID);
        jdbcTemplate.update("""
                insert into auction
                    (id, post_id, start_price, current_price, room_open_at, start_time,
                     current_end_time, extension_count, status, created_at, updated_at)
                values (?, ?, 10000000, null,
                        DATE_SUB(NOW(6), INTERVAL 30 MINUTE), NOW(6),
                        DATE_ADD(NOW(6), INTERVAL 20 MINUTE), 0, ?, NOW(6), NOW(6))
                """, auctionId, auctionId, auctionStatus);
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
                          "diagnosticReportUrl": "%s",
                          "keywords": ["ACCIDENT_FREE", "NO_LEAK"]
                        }
                        """.formatted(IMAGE_URL, DOCUMENT_URL)));
    }

    private static Cookie cookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }
}
