package com.softeer.race.vehicle.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>실물 사진을 등록하면 카탈로그 이미지가 사라지고 대표 이미지가 실물 첫 장이 된다</li>
 *   <li>다시 등록하면 이전 사진이 남지 않고 통째로 교체된다</li>
 *   <li>우리가 발급하지 않은 주소는 거부되고 기존 사진이 그대로 남는다</li>
 *   <li>세션이 없으면 401</li>
 * </ol>
 * <p>
 * 경매글 썸네일은 검증하지 않는다. 경매는 사진 등록 이후(출품 동의 시점)에 만들어지고 그때
 * {@code AuctionService.create}가 여기서 저장한 첫 장을 집어 가므로, 이 유스케이스가 손댈 것이 없다.
 * <p>
 * 시각을 고정하지 않는다. 이 유스케이스에 시각 의존 로직이 없다.
 */
@DisplayName("차량 사진 등록 통합 테스트")
class VehicleImageIntegrationTest extends IntegrationTestSupport {

    private static final String RAW_TOKEN = "vehicle-image-raw-token";

    /** 판매 신청이 카탈로그에서 복제해 넣는 제조사 홍보 이미지 */
    private static final String CATALOG_IMAGE = "https://cdn.race.dev/vehicles/catalog.jpg";

    /** 테스트 설정의 aws.s3.cdn-base-url 이 https://cdn.test.local 이라 그 아래여야 통과한다 */
    private static final String REAL_IMAGE_1 = "https://cdn.test.local/images/2026/08/real-1.jpg";
    private static final String REAL_IMAGE_2 = "https://cdn.test.local/images/2026/08/real-2.jpg";
    private static final String REAL_IMAGE_3 = "https://cdn.test.local/images/2026/08/real-3.jpg";

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("실물 사진을 등록하면 카탈로그 이미지가 사라지고 대표 이미지가 실물 첫 장이 된다")
    void scenario1_ReplacesCatalogImage() throws Exception {
        // given : 판매 신청 직후 상태 — 카탈로그 홍보 이미지 한 장만 있다
        Fixture fixture = givenVehicleWithCatalogImage();

        // when
        ResultActions response = register(fixture.vehicleId(), fixture.cookie(), REAL_IMAGE_1, REAL_IMAGE_2);

        // then 1 : 응답
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.thumbnailUrl").value(REAL_IMAGE_1));

        // then 2 : 카탈로그 이미지를 남겨 두면 대표 이미지 규칙이 sortOrder 최솟값이라
        //          실물을 등록해도 홍보 이미지가 계속 대표로 남는다
        assertThat(imageUrlsOf(fixture.vehicleId())).containsExactly(REAL_IMAGE_1, REAL_IMAGE_2);

        // then 3 : 경매 생성이 이 sortOrder 최솟값을 대표 이미지로 집어 간다.
        //          그 규칙이 실제로 실물 첫 장을 가리키는지 확인한다
        assertThat(firstImageOf(fixture.vehicleId())).isEqualTo(REAL_IMAGE_1);
    }

    @Test
    @DisplayName("다시 등록하면 이전 사진이 남지 않고 통째로 교체된다")
    void scenario2_ReplacesPreviousImages() throws Exception {
        // given : 이미 실물 두 장이 등록된 상태
        Fixture fixture = givenVehicleWithCatalogImage();
        register(fixture.vehicleId(), fixture.cookie(), REAL_IMAGE_1, REAL_IMAGE_2);

        // when : 세 번째 사진 한 장만 다시 보낸다
        register(fixture.vehicleId(), fixture.cookie(), REAL_IMAGE_3)
                .andExpect(status().isOk());

        // then : 부분 추가가 아니라 교체다
        assertThat(imageUrlsOf(fixture.vehicleId())).containsExactly(REAL_IMAGE_3);
    }

    @Test
    @DisplayName("발급하지 않은 주소는 거부되고 기존 사진이 그대로 남는다")
    void scenario3_RejectsUnmanagedUrl() throws Exception {
        // given
        Fixture fixture = givenVehicleWithCatalogImage();

        // when : 첫 장은 우리 주소지만 두 번째가 외부 주소다
        register(fixture.vehicleId(), fixture.cookie(), REAL_IMAGE_1, "https://evil.example.com/x.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VEHICLE_UNMANAGED_IMAGE_URL"));

        // then : 검증이 삭제보다 먼저 일어나지 않으면 여기서 기존 사진까지 잃는다
        assertThat(imageUrlsOf(fixture.vehicleId())).containsExactly(CATALOG_IMAGE);
    }

    @Test
    @DisplayName("세션이 없으면 401")
    void scenario4_RequiresLogin() throws Exception {
        // given
        Fixture fixture = givenVehicleWithCatalogImage();

        // when & then
        mockMvc.perform(post("/api/vehicles/{vehicleId}/images", fixture.vehicleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REAL_IMAGE_1)))
                .andExpect(status().isUnauthorized());

        assertThat(imageUrlsOf(fixture.vehicleId())).containsExactly(CATALOG_IMAGE);
    }

    /**
     * 판매 신청을 마친 직후와 같은 상태를 만든다 — 카탈로그 홍보 이미지 한 장이 차량에 붙어 있다.
     * <p>
     * 차량을 직접 만드는 시더가 없어 경매방 시더를 쓴다. 경매까지 함께 생기지만 이 유스케이스는
     * 경매를 보지 않으므로 무해하고, 차량을 도메인 생성 경로로 만들어 준다는 이점이 있다.
     * <p>
     * 사진을 올리는 주체는 평가사지만 아직 역할 검사가 없어 어느 계정이든 통과한다. 역할이
     * 생겼을 때 이 픽스처가 그대로 유효하도록 EVALUATOR로 만들어 둔다.
     */
    private Fixture givenVehicleWithCatalogImage() {
        User evaluator = users.user("김평가", Role.EVALUATOR);
        User seller = users.user("박판매", Role.GENERAL);

        long auctionId = rooms.room(seller, LocalDateTime.now(clock).plusHours(2))
                .thumbnailUrl(CATALOG_IMAGE)
                .create();
        long vehicleId = vehicleIdOf(auctionId);
        insertCatalogImage(vehicleId);

        return new Fixture(vehicleId, login(evaluator));
    }

    /**
     * 시더에 세션을 만드는 기능이 없어 직접 넣는다. 세션 PK는 원문 토큰의 SHA-256 hex라
     * 픽스처가 같은 방식으로 만들어야 쿠키가 맞아떨어진다.
     */
    private Cookie login(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update("""
                        insert into user_session (id, user_id, expires_at, created_at, updated_at)
                        values (sha2(?, 256), ?, ?, ?, ?)
                        """,
                RAW_TOKEN, user.getId(), now.plusHours(1), now, now);

        return new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN);
    }

    private long vehicleIdOf(long auctionId) {
        Long vehicleId = jdbcTemplate.queryForObject("""
                select p.vehicle_id from auction a join auction_post p on a.post_id = p.id where a.id = ?
                """, Long.class, auctionId);

        assertThat(vehicleId).isNotNull();
        return vehicleId;
    }

    private void insertCatalogImage(long vehicleId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update("""
                        insert into vehicle_image (vehicle_id, image_url, sort_order, created_at, updated_at)
                        values (?, ?, 1, ?, ?)
                        """,
                vehicleId, CATALOG_IMAGE, now, now);
    }

    private List<String> imageUrlsOf(long vehicleId) {
        return jdbcTemplate.queryForList(
                "select image_url from vehicle_image where vehicle_id = ? order by sort_order",
                String.class, vehicleId);
    }

    /** 대표 이미지 규칙(sortOrder 최솟값)이 가리키는 값. 경매 생성 시 이 값이 썸네일이 된다 */
    private String firstImageOf(long vehicleId) {
        return jdbcTemplate.queryForObject("""
                select image_url from vehicle_image where vehicle_id = ? order by sort_order limit 1
                """, String.class, vehicleId);
    }

    private ResultActions register(long vehicleId, Cookie cookie, String... imageUrls) throws Exception {
        return mockMvc.perform(post("/api/vehicles/{vehicleId}/images", vehicleId)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(imageUrls)));
    }

    private static String body(String... imageUrls) {
        String urls = String.join(",", java.util.Arrays.stream(imageUrls)
                .map("\"%s\""::formatted)
                .toList());

        return "{\"imageUrls\": [" + urls + "]}";
    }

    private record Fixture(long vehicleId, Cookie cookie) {
    }
}
