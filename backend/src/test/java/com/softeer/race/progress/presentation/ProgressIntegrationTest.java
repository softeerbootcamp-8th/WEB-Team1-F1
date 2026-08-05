package com.softeer.race.progress.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진행 상황 조회를 컨트롤러에서 DB까지
 * <p>
 * 쿼리 자체는 저장소 테스트가 본다. 여기서 보는 것은 그 위에 얹힌 것들이다 —
 * 로그인한 사람의 것만 나오는지, 남의 담당이 새지 않는지, 단계마다 비는 값이 응답에서도
 * 비어 있는지.
 * <p>
 * 역할 검사는 없다. 평가사 목록도 지금은 로그인만 확인하며, 그 범위를 좁히는 것은 서비스 전체에
 * 역할 기반 인가가 들어올 때 함께 다룬다.
 */
@DisplayName("진행 상황 조회 통합 테스트")
@Sql("/sql/progress-fixture.sql")
class ProgressIntegrationTest extends IntegrationTestSupport {

    private static final String SELLER_TOKEN = "progress-seller-token";
    private static final String GENERAL_TOKEN = "progress-general-token";
    private static final String EVALUATOR_TOKEN = "progress-evaluator-token";

    // 세션 만료가 이 시각 뒤라야 인터셉터를 통과한다
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @BeforeEach
    void fixTime() {
        fixClockAt(FIXED_NOW);
    }

    // ================= 판매자 =================

    @Test
    @DisplayName("시나리오 1 : 판매 신청과 방문견적이 한 목록에 섞여 최근 순으로 나온다")
    void scenario1_ListsBothPathsNewestFirst() throws Exception {
        listMine(SELLER_TOKEN)
                .andExpect(status().isOk())
                // 평가도 경매글도 없는 210 과 남의 차 211 을 뺀 아홉 대
                .andExpect(jsonPath("$.content.length()").value(9))
                .andExpect(jsonPath("$.content[0].vehicleId").value(209))
                .andExpect(jsonPath("$.content[0].stage").value("LISTING_REMOVED"))
                // 방문견적으로 들어온 건은 아직 경매가 없어 경매방으로 갈 수 없다
                .andExpect(jsonPath("$.content[8].stage").value("EVALUATION_REQUESTED"))
                .andExpect(jsonPath("$.content[8].auctionId").doesNotExist())
                // 판매 신청으로 들어온 건은 처음부터 경매가 있다
                .andExpect(jsonPath("$.content[4].stage").value("AUCTION_SCHEDULED"))
                .andExpect(jsonPath("$.content[4].auctionId").value(205));
    }

    @Test
    @DisplayName("시나리오 2 : 남의 진행 상황은 목록에도 상세에도 없다")
    void scenario2_OtherSellersProgressIsInvisible() throws Exception {
        // 판매자 201 에게는 자기 차 한 대만 보인다
        listMine(GENERAL_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].vehicleId").value(211));

        // 남의 차량 : 권한 문제로 답하면 그 차량이 존재한다는 사실이 드러난다
        detail(SELLER_TOKEN, 211L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROGRESS_PROGRESS_NOT_FOUND"));

        // 없는 차량 : 위와 응답이 같아 요청한 쪽이 둘을 구분할 수 없다
        detail(SELLER_TOKEN, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROGRESS_PROGRESS_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 3 : 상세는 단계에서만 의미가 있는 값까지 내려준다")
    void scenario3_DetailCarriesStageSpecificValues() throws Exception {
        // 반려된 건 : 사유가 있고 경매 값은 전부 빈다
        detail(SELLER_TOKEN, 203L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EVALUATION_REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("침수 이력이 확인됩니다."))
                .andExpect(jsonPath("$.visitDate").value("2026-08-04"))
                .andExpect(jsonPath("$.auctionId").doesNotExist())
                .andExpect(jsonPath("$.startPrice").doesNotExist())
                // 진단 전이라 주행거리와 예상 시세가 아직 없다
                .andExpect(jsonPath("$.mileage").doesNotExist())
                .andExpect(jsonPath("$.estimatedPrice").doesNotExist());

        // 낙찰된 건 : 현재가가 곧 낙찰가이고 평가 값은 전부 빈다
        detail(SELLER_TOKEN, 207L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUCTION_WON"))
                .andExpect(jsonPath("$.currentPrice").value(23000000))
                .andExpect(jsonPath("$.visitDate").doesNotExist())
                .andExpect(jsonPath("$.rejectReason").doesNotExist());

        // 유찰된 건 : 입찰이 없어 현재가가 비어 있다
        detail(SELLER_TOKEN, 208L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUCTION_FAILED"))
                .andExpect(jsonPath("$.startPrice").value(25000000))
                .andExpect(jsonPath("$.currentPrice").doesNotExist());
    }

    // ================= 평가사 =================

    @Test
    @DisplayName("시나리오 4 : 평가사는 내 담당과 아직 아무도 맡지 않은 신청을 함께 본다")
    void scenario4_EvaluatorSeesOwnAndUnassigned() throws Exception {
        evaluatorTasks(EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                // 203(8/4) → 204(8/5) → 202(8/6) → 201(8/10). 다른 평가사가 맡은 205 는 빠진다
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].evaluationId").value(203))
                .andExpect(jsonPath("$.content[0].group").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].group").value("COMPLETED"))
                .andExpect(jsonPath("$.content[2].evaluationId").value(202))
                .andExpect(jsonPath("$.content[2].group").value("ASSIGNED"))
                .andExpect(jsonPath("$.content[3].evaluationId").value(201))
                .andExpect(jsonPath("$.content[3].group").value("UNASSIGNED"))
                // 신청자를 알아야 방문을 잡을 수 있다
                .andExpect(jsonPath("$.content[3].sellerName").value("박판매"))
                .andExpect(jsonPath("$.content[3].visitAddress").value("서울 강남구 테헤란로 1"));
    }

    @Test
    @DisplayName("시나리오 5 : 다른 평가사가 맡은 신청은 보이지 않는다")
    void scenario5_HidesOtherEvaluatorsTask() throws Exception {
        // 평가사 211 이 맡은 205 는 어디에도 없다. 남의 담당까지 보여줄 이유가 없고 신청자 주소가 함께 나간다
        evaluatorTasks(EVALUATOR_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.evaluationId == 205)]").isEmpty());
    }

    @Test
    @DisplayName("시나리오 6 : 로그인하지 않으면 어느 쪽도 열리지 않는다")
    void scenario6_RequiresLogin() throws Exception {
        mockMvc.perform(get("/api/progress/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/progress/me/{id}", 201L)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/progress/evaluations")).andExpect(status().isUnauthorized());
    }

    private ResultActions listMine(String token) throws Exception {
        return mockMvc.perform(get("/api/progress/me").cookie(sessionCookie(token)));
    }

    private ResultActions detail(String token, long vehicleId) throws Exception {
        return mockMvc.perform(get("/api/progress/me/{id}", vehicleId).cookie(sessionCookie(token)));
    }

    private ResultActions evaluatorTasks(String token) throws Exception {
        return mockMvc.perform(get("/api/progress/evaluations").cookie(sessionCookie(token)));
    }

    private static Cookie sessionCookie(String token) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, token);
    }
}
