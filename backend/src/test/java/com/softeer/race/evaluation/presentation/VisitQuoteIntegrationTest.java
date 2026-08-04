package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방문견적 신청을 컨트롤러에서 DB까지
 * <p>
 * 1. 접수
 * 방문 정보 하나로 차량과 평가 요청이 만들어지고, 배정 대기 상태로 남는지
 * <p>
 * 2. 경매 미생성
 * 판매 신청과 달리 경매글·경매가 생기지 않는지. 두 흐름을 갈라놓은 이유가 여기 있다
 * <p>
 * 3. 중복 차단
 * 같은 번호판으로 진행 중인 신청이 있으면 막히는지
 * <p>
 * 4. 신청자 무관
 * 다른 회원이 같은 번호판으로 신청해도 막히는지. 중복 기준에 신청자를 넣지 않은 결정을 고정한다
 * <p>
 * 5. 반려 후 재신청
 * REJECTED는 종료 상태라 같은 차를 다시 신청할 수 있는지
 * <p>
 * 6. 인증
 * 인터셉터 화이트리스트가 실제로 걸려 있는지
 * <p>
 * 7. 소유자 대조
 * 로그인한 회원이 남의 차로 신청할 수 없는지. 세션이 소유를 증명하지 않는다는 사실을 고정한다
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료 시각이 DB의 실제 시각(NOW(6))으로 심기므로,
 * 앱 Clock만 과거·미래로 옮기면 전 시나리오가 401이나 세션 만료로 깨진다. 대신 방문 날짜를
 * 주입된 Clock에서 상대적으로 계산해 해가 바뀌어도 깨지지 않게 한다.
 * <p>
 * 그 대가로 <b>금액을 정확한 값으로 검증하지 못한다.</b> 예상 시세가 연식 나이에서 계산되므로
 * 하드코딩하면 1월 1일에 전 시나리오가 깨진다. 여기서는 관계만 본다 — 기준가보다 낮고 만원 단위로
 * 떨어진다는 것. 정확한 금액은 Clock을 고정한 {@code VisitQuoteServiceTest}와
 * {@code QuotePolicyTest}가 맡는다.
 * <p>
 * 차량 카탈로그는 시세 조회·판매 신청과 같은 픽스처를 쓴다. 따로 시드하면 기준가가 갈라져
 * "시세 조회가 보여준 금액으로 신청이 접수된다"를 테스트가 더 이상 보증하지 못한다.
 */
@DisplayName("방문견적 신청 통합 테스트")
@Transactional
@Sql({"/sql/vehicle-catalog-fixture.sql", "/sql/visit-quote-fixture.sql"})
class VisitQuoteIntegrationTest extends IntegrationTestSupport {

    private static final long SELLER_ID = 400L;
    private static final String RAW_TOKEN = "visit-quote-raw-token";
    private static final String OTHER_RAW_TOKEN = "visit-quote-other-raw-token";

    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String OTHER_PLATE_NUMBER = "34나5678";
    private static final String OTHER_OWNER_NAME = "이서연";
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    /** 픽스처 201번의 기준가. 그 모델의 신차급 가격이라 응답으로 나가면 안 되는 값이다 */
    private static final long CATALOG_BASE_PRICE = 34_000_000L;

    /** 예상 시세는 만원 단위로 절사돼 나간다, 원 단위 잔돈이 붙으면 정책을 거치지 않은 값이다 */
    private static final long DISPLAY_UNIT = 10_000L;

    // 고정하지 않은 실제 Clock이다, 방문 날짜를 여기서 상대적으로 만든다
    @Autowired
    private Clock clock;

    @Test
    @DisplayName("시나리오 1 : 방문 정보를 보내면 차량과 배정 대기 상태의 신청이 함께 만들어진다")
    void scenario1_CreatesVehicleAndPendingEvaluation() throws Exception {
        // given : 픽스처에 차량도 신청도 없다
        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
        LocalDate visitDate = today().plusDays(16);

        // when
        MvcResult result = request(PLATE_NUMBER, visitDate)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.plateNumber").value(PLATE_NUMBER))
                .andExpect(jsonPath("$.visitDate").value(visitDate.toString()))
                .andExpect(jsonPath("$.visitAddress").value(VISIT_ADDRESS))
                // 연락처는 응답에 실리지 않는다. 필드가 하나 늘는 것만으로 무너지므로 못 박는다
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andReturn();

        // then 1 : 차량은 클라이언트가 보내지 않은 제원까지 서버가 조회해 채웠다
        Map<String, Object> vehicle = rowOf("select * from vehicle");
        assertThat(vehicle.get("seller_id")).isEqualTo(SELLER_ID);
        assertThat(vehicle.get("plate_number")).isEqualTo(PLATE_NUMBER);
        assertThat(vehicle.get("manufacturer")).isEqualTo("HYUNDAI");
        assertThat(vehicle.get("model")).isEqualTo("그랜저 IG");
        assertThat(vehicle.get("model_year")).isEqualTo(2021);
        assertThat(vehicle.get("mileage")).isEqualTo(45_000);

        // then 2 : 예상 시세는 기준가가 아니라 감가를 반영한 값이고, 응답과 차량이 같은 값을 쓴다
        long estimatedPrice = ((Number) vehicle.get("estimated_price")).longValue();
        assertThat(estimatedPrice).isLessThan(CATALOG_BASE_PRICE);
        assertThat(estimatedPrice).isPositive();
        assertThat(estimatedPrice % DISPLAY_UNIT).isZero();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"estimatedPrice\":" + estimatedPrice)
                // 기준가는 어디에도 남지 않는다
                .doesNotContain(String.valueOf(CATALOG_BASE_PRICE));

        // then 3 : 신청은 접수 상태이고 평가사가 배정되지 않았다 — 이것이 "배정 대기"다
        Map<String, Object> evaluation = rowOf("select * from evaluation");
        assertThat(evaluation.get("status")).isEqualTo("REQUESTED");
        assertThat(evaluation.get("evaluator_id")).isNull();
        assertThat(evaluation.get("reject_reason")).isNull();
        assertThat(evaluation.get("vehicle_id")).isEqualTo(vehicle.get("id"));

        // then 4 : 방문 정보가 요청 그대로 남는다. 연락처는 응답에는 없지만 저장은 돼야 한다
        assertThat(evaluation.get("visit_date")).hasToString(visitDate.toString());
        assertThat(evaluation.get("visit_address")).isEqualTo(VISIT_ADDRESS);
        assertThat(evaluation.get("contact_phone")).isEqualTo(CONTACT_PHONE);
    }

    // 판매 신청과 방문견적을 갈라놓은 이유가 실제로 지켜지는지 확인하는 유일한 자리다
    // 출품은 진단이 끝난 뒤의 단계라, 신청만으로 경매가 생기면 미진단 차량이 경매 목록에 노출된다
    @Test
    @DisplayName("시나리오 2 : 신청만으로는 경매글도 경매도 만들어지지 않는다")
    void scenario2_DoesNotCreateAuction() throws Exception {
        request(PLATE_NUMBER, today().plusDays(16)).andExpect(status().isCreated());

        assertThat(countOf("evaluation")).isEqualTo(1);
        assertThat(countOf("auction_post")).isZero();
        assertThat(countOf("auction")).isZero();
    }

    @Test
    @DisplayName("시나리오 3 : 같은 번호판으로 다시 신청하면 409로 막히고 신청은 한 건만 남는다")
    void scenario3_DuplicateRequestIsRejected() throws Exception {
        // given
        request(PLATE_NUMBER, today().plusDays(16)).andExpect(status().isCreated());

        // when
        request(PLATE_NUMBER, today().plusDays(20))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_DUPLICATE_REQUEST"));

        // then : 차량도 신청도 늘지 않는다. 차단이 vehicle insert보다 먼저 일어난다는 뜻이다
        assertThat(countOf("evaluation")).isEqualTo(1);
        assertThat(countOf("vehicle")).isEqualTo(1);

        // 다른 번호판은 영향을 받지 않는다
        request(OTHER_PLATE_NUMBER, OTHER_OWNER_NAME, today().plusDays(16))
                .andExpect(status().isCreated());
        assertThat(countOf("evaluation")).isEqualTo(2);
    }

    // 중복 기준에 신청자를 넣지 않기로 한 결정을 고정한다
    // 평가사가 한 차량에 두 번 방문하는 일이 없어야 하고, 그건 신청자가 누구든 마찬가지다
    @Test
    @DisplayName("시나리오 4 : 다른 회원이 같은 번호판으로 신청해도 409로 막힌다")
    void scenario4_DuplicateAcrossSellers() throws Exception {
        request(PLATE_NUMBER, today().plusDays(16)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/visit-quotes")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, OTHER_RAW_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PLATE_NUMBER, today().plusDays(16))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_DUPLICATE_REQUEST"));

        assertThat(countOf("evaluation")).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 5 : 반려된 신청은 진행 중이 아니므로 같은 차를 다시 신청할 수 있다")
    void scenario5_RejectedAllowsReapply() throws Exception {
        // given : 첫 신청이 반려됐다
        request(PLATE_NUMBER, today().plusDays(16)).andExpect(status().isCreated());
        jdbcTemplate.update("update evaluation set status = 'REJECTED', reject_reason = ?",
                "차량 상태가 기준에 맞지 않습니다.");

        // when
        request(PLATE_NUMBER, today().plusDays(20)).andExpect(status().isCreated());

        // then : 반려된 건과 새 건이 함께 남는다
        assertThat(countOf("evaluation")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from evaluation where status = 'REQUESTED'", Integer.class))
                .isEqualTo(1);
    }

    // 화이트리스트에서 /api/visit-quotes가 빠지면 이 테스트만 통과하고 시나리오 1~5가 전부 깨진다
    // 반대로 화이트리스트만 있고 @LoginUser가 없으면 여기가 깨진다
    @Test
    @DisplayName("시나리오 6 : 세션 쿠키 없이 신청하면 401이다")
    void scenario6_RequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/visit-quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PLATE_NUMBER, today().plusDays(16))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
    }

    @Test
    @DisplayName("시나리오 7 : 과거 날짜는 400이고 아무것도 남지 않는다")
    void scenario7_PastVisitDate() throws Exception {
        request(PLATE_NUMBER, today().minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVALUATION_PAST_VISIT_DATE"));

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
    }

    // 오늘 방문 요청은 허용한다. 경계라 isBefore를 다른 비교로 바꾸면 여기서만 깨진다
    @Test
    @DisplayName("시나리오 8 : 오늘 날짜는 방문 희망일로 허용된다")
    void scenario8_TodayIsAllowed() throws Exception {
        request(PLATE_NUMBER, today()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("시나리오 9 : 카탈로그에 없는 번호판은 404이고 아무것도 남지 않는다")
    void scenario9_UnknownPlateNumber() throws Exception {
        request("99하9999", today().plusDays(16))
                .andExpect(status().isNotFound())
                // 접두사가 없으면 SellErrorCode·AuctionErrorCode의 같은 이름과 구별할 수 없다
                .andExpect(jsonPath("$.code").value("EVALUATION_VEHICLE_NOT_FOUND"));

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
    }

    // 로그인만으로는 소유를 증명할 수 없다는 결정을 고정한다
    // findByPlateNumber로 되돌리면 이 시나리오만 깨지고 나머지는 전부 통과한다
    @Test
    @DisplayName("시나리오 10 : 소유자명이 어긋나면 남의 차로 신청할 수 없다")
    void scenario10_OwnerNameMismatchIsRejected() throws Exception {
        request(PLATE_NUMBER, "남의이름", today().plusDays(16))
                .andExpect(status().isNotFound())
                // 미등록과 같은 코드다. 갈라지면 번호판을 바꿔 넣어보며 소유자명을 역추적할 수 있다
                .andExpect(jsonPath("$.code").value("EVALUATION_VEHICLE_NOT_FOUND"));

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
    }

    @Test
    @DisplayName("시나리오 11 : 하이픈이 섞인 연락처는 400이다")
    void scenario11_HyphenatedPhoneIsRejected() throws Exception {
        mockMvc.perform(post("/api/visit-quotes")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber": "%s", "ownerName": "%s", "visitAddress": "%s",
                                 "visitDate": "%s", "contactPhone": "010-1234-5678"}
                                """.formatted(PLATE_NUMBER, OWNER_NAME, VISIT_ADDRESS,
                                today().plusDays(16))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(countOf("evaluation")).isZero();
    }

    // ================= 요청 =================

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ResultActions request(String plateNumber, LocalDate visitDate) throws Exception {
        return request(plateNumber, OWNER_NAME, visitDate);
    }

    private ResultActions request(String plateNumber, String ownerName, LocalDate visitDate)
            throws Exception {
        return mockMvc.perform(post("/api/visit-quotes")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(plateNumber, ownerName, visitDate)));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN);
    }

    private static String body(String plateNumber, LocalDate visitDate) {
        return body(plateNumber, OWNER_NAME, visitDate);
    }

    private static String body(String plateNumber, String ownerName, LocalDate visitDate) {
        return """
                {"plateNumber": "%s", "ownerName": "%s", "visitAddress": "%s",
                 "visitDate": "%s", "contactPhone": "%s"}
                """.formatted(plateNumber, ownerName, VISIT_ADDRESS, visitDate, CONTACT_PHONE);
    }

    // ================= 조회 =================

    private Integer countOf(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Integer.class);
    }

    private Map<String, Object> rowOf(String sql) {
        return jdbcTemplate.queryForMap(sql);
    }
}
