package com.softeer.race.sell.presentation;

import com.jayway.jsonpath.JsonPath;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판매 신청을 컨트롤러에서 DB까지
 * <p>
 * 1. 생성
 * 번호판 하나로 차량 · 이미지 · 경매글 · 경매가 한 트랜잭션에 만들어지는지
 * <p>
 * 2. 목록 노출
 * 신청한 경매가 곧바로 경매 목록에 뜨는지. 이 유스케이스의 존재 이유다
 * <p>
 * 3. 중복 허용
 * 같은 번호판을 반복 신청해도 매번 새로 만들어지는지
 * <p>
 * 4. 이미지 없는 차량
 * 썸네일 없는 경로가 실제로 동작하는지
 * <p>
 * 5. 인증
 * 인터셉터 화이트리스트가 실제로 걸려 있는지
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 이 API가 가장 깨지기 쉬운 지점은 발행 시각과 시작 시각을 서로 다른
 * 시각에서 계산해 최소 리드타임 검증이 항상 실패하는 것인데, 그 버그는 고정 Clock에서는 두 값이 같은
 * 틱에 떨어져 재현되지 않는다. 실제 Clock으로 돌려야 회귀 방어가 성립한다.
 */
@DisplayName("판매 신청 통합 테스트")
@Transactional
@Sql("/sql/sell-application-fixture.sql")
class SellApplicationIntegrationTest extends IntegrationTestSupport {

    private static final long SELLER_ID = 90L;
    private static final String RAW_TOKEN = "sell-raw-token";

    private static final String PLATE_WITH_IMAGE = "12가3456";
    private static final String PLATE_WITHOUT_IMAGE = "90마5678";
    private static final String IMAGE_URL = "https://cdn.race.dev/vehicles/grandeur-ig.jpg";
    private static final long BASE_PRICE = 24_800_000L;

    // 고정하지 않은 실제 Clock이다, 시각은 값이 아니라 범위로 검증한다
    @Autowired
    private Clock clock;

    @Test
    @DisplayName("시나리오 1 : 번호판만 보내면 차량 · 이미지 · 경매글 · 경매가 함께 만들어진다")
    void scenario1_CreatesEverythingFromPlateNumber() throws Exception {
        // given : 픽스처에 차량이 없다
        assertThat(countOf("vehicle")).isZero();
        LocalDateTime before = LocalDateTime.now(clock);

        // when
        MvcResult result = apply(PLATE_WITH_IMAGE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.startPrice").value(BASE_PRICE))
                .andReturn();

        LocalDateTime after = LocalDateTime.now(clock);

        // then 1 : 시작 시각은 최소 리드타임(1시간)을 넘기고 분 단위로 떨어진다
        // 시각을 두 번 읽는 구현이면 여기가 아니라 위 status().isCreated()가 400 INVALID_START_AT으로 깨진다
        LocalDateTime startAt = LocalDateTime.parse(json(result, "$.startAt"));
        assertThat(startAt).isAfter(before.plusHours(1));
        assertThat(startAt).isBeforeOrEqualTo(after.plusHours(1).plusMinutes(1));
        assertThat(startAt.getSecond()).isZero();

        // then 2 : 응답이 시작 · 입장 · 마감 시각을 한 번에 안내한다
        assertThat(LocalDateTime.parse(json(result, "$.roomOpenAt"))).isEqualTo(startAt.minusMinutes(30));
        assertThat(LocalDateTime.parse(json(result, "$.endAt"))).isEqualTo(startAt.plusMinutes(20));

        // then 3 : 차량은 클라이언트가 보내지 않은 제원까지 서버가 조회해 채웠다
        Map<String, Object> vehicle = rowOf("select * from vehicle");
        assertThat(vehicle.get("seller_id")).isEqualTo(SELLER_ID);
        assertThat(vehicle.get("plate_number")).isEqualTo(PLATE_WITH_IMAGE);
        assertThat(vehicle.get("manufacturer")).isEqualTo("HYUNDAI");
        assertThat(vehicle.get("model")).isEqualTo("그랜저 IG");
        assertThat(vehicle.get("model_year")).isEqualTo(2021);
        assertThat(vehicle.get("mileage")).isEqualTo(45_000);
        assertThat(vehicle.get("fuel_type")).isEqualTo("GASOLINE");
        assertThat(vehicle.get("transmission")).isEqualTo("AUTOMATIC");
        assertThat(vehicle.get("estimated_price")).isEqualTo(BASE_PRICE);

        // then 4 : 대표 이미지는 sortOrder 최솟값 규칙에 맞게 1로 저장된다
        Map<String, Object> image = rowOf("select * from vehicle_image");
        assertThat(image.get("image_url")).isEqualTo(IMAGE_URL);
        assertThat(image.get("sort_order")).isEqualTo(1);

        // then 5 : 경매글은 임시저장 없이 곧바로 발행된다
        Map<String, Object> post = rowOf("select * from auction_post");
        assertThat(post.get("post_status")).isEqualTo("PUBLISHED");
        assertThat(post.get("thumbnail_url")).isEqualTo(IMAGE_URL);
        assertThat(post.get("deleted_at")).isNull();

        // then 6 : 경매의 파생 시각이 시작 시각에서 계산된다
        Map<String, Object> auction = rowOf("select * from auction");
        assertThat(auction.get("start_time")).isEqualTo(startAt);
        assertThat(auction.get("room_open_at")).isEqualTo(startAt.minusMinutes(30));
        assertThat(auction.get("current_end_time")).isEqualTo(startAt.plusMinutes(20));
        assertThat(auction.get("start_price")).isEqualTo(BASE_PRICE);
        assertThat(auction.get("status")).isEqualTo("SCHEDULED");
    }

    // 평가 요청을 만들지 않고 곧바로 발행하기로 한 결정이 실제로 목적을 달성하는지 확인하는 유일한 자리다
    @Test
    @DisplayName("시나리오 2 : 신청한 경매가 곧바로 경매 목록에 노출된다")
    void scenario2_AppearsInAuctionListImmediately() throws Exception {
        // given : 판매 신청
        MvcResult created = apply(PLATE_WITH_IMAGE).andExpect(status().isCreated()).andReturn();
        int auctionId = JsonPath.read(created.getResponse().getContentAsString(), "$.auctionId");

        // when : 같은 트랜잭션이지만 JPQL 실행 전 auto-flush가 일어나 위 쓰기가 보인다
        // then : 시작이 1시간 뒤라 예정 그룹(NOT_OPEN)에 잡히고, 카드 값은 조회된 제원 그대로다
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].auctionId").value(auctionId))
                .andExpect(jsonPath("$.content[0].phase").value("NOT_OPEN"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value(IMAGE_URL))
                .andExpect(jsonPath("$.content[0].model").value("그랜저 IG"))
                .andExpect(jsonPath("$.content[0].modelYear").value(2021))
                .andExpect(jsonPath("$.content[0].mileage").value(45000))
                .andExpect(jsonPath("$.content[0].startPrice").value(BASE_PRICE))
                // 입찰이 없으므로 현재가는 시작가로 채워진다
                .andExpect(jsonPath("$.content[0].currentPrice").value(BASE_PRICE));
    }

    // 중복 검사를 넣지 않기로 한 결정을 고정한다
    // 매번 새 차량이 생기므로 auction_post.vehicle_id의 unique 제약과도 충돌하지 않는다
    @Test
    @DisplayName("시나리오 3 : 같은 번호판으로 다시 신청해도 각각 새로 만들어진다")
    void scenario3_DuplicatePlateNumberIsAllowed() throws Exception {
        // when
        MvcResult first = apply(PLATE_WITH_IMAGE).andExpect(status().isCreated()).andReturn();
        MvcResult second = apply(PLATE_WITH_IMAGE).andExpect(status().isCreated()).andReturn();

        // then 1 : 서로 다른 경매다
        assertThat((int) JsonPath.read(first.getResponse().getContentAsString(), "$.auctionId"))
                .isNotEqualTo((int) JsonPath.read(second.getResponse().getContentAsString(), "$.auctionId"));

        // then 2 : 차량부터 경매까지 전부 두 벌이다
        assertThat(countOf("vehicle")).isEqualTo(2);
        assertThat(countOf("auction_post")).isEqualTo(2);
        assertThat(countOf("auction")).isEqualTo(2);
    }

    @Test
    @DisplayName("시나리오 4 : 대표 이미지가 없는 차량은 썸네일 없이 등록되고 목록 카드도 이미지 없이 나간다")
    void scenario4_VehicleWithoutImage() throws Exception {
        // when
        apply(PLATE_WITHOUT_IMAGE).andExpect(status().isCreated());

        // then 1 : 이미지 row 자체가 없다
        assertThat(countOf("vehicle_image")).isZero();
        assertThat(rowOf("select * from auction_post").get("thumbnail_url")).isNull();

        // then 2 : 화면이 이미지 없음을 처리해야 한다는 사실이 드러난다
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].model").value("520i"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").doesNotExist());
    }

    // 인터셉터 화이트리스트에서 /api/sell이 빠지면 이 테스트만 통과하고 시나리오 1~4가 전부 깨진다
    // 반대로 화이트리스트만 있고 @LoginUser가 없으면 여기가 깨진다
    @Test
    @DisplayName("시나리오 5 : 세션 쿠키 없이 신청하면 401이다")
    void scenario5_RequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PLATE_WITH_IMAGE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));

        assertThat(countOf("vehicle")).isZero();
    }

    @Test
    @DisplayName("시나리오 6 : 카탈로그에 없는 번호판은 404이고 아무것도 남지 않는다")
    void scenario6_UnknownPlateNumber() throws Exception {
        apply("99하9999")
                .andExpect(status().isNotFound())
                // 접두사가 없으면 AuctionErrorCode.VEHICLE_NOT_FOUND와 같은 문자열이 되어 원인을 못 가린다
                .andExpect(jsonPath("$.code").value("SELL_VEHICLE_NOT_FOUND"));

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("auction")).isZero();
    }

    @Test
    @DisplayName("시나리오 7 : 형식에 맞지 않는 번호판은 400이다")
    void scenario7_InvalidPlateNumberFormat() throws Exception {
        apply("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 정규화하지 않기로 했으므로 공백이 섞인 값도 여기서 막힌다
        apply("12가 3456").andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ================= 요청 =================

    private org.springframework.test.web.servlet.ResultActions apply(String plateNumber) throws Exception {
        return mockMvc.perform(post("/api/sell")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(plateNumber)));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN);
    }

    private static String body(String plateNumber) {
        return """
                {"plateNumber": "%s"}
                """.formatted(plateNumber);
    }

    // ================= 조회 =================

    private static String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private Integer countOf(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Integer.class);
    }

    private Map<String, Object> rowOf(String sql) {
        return jdbcTemplate.queryForMap(sql);
    }
}
