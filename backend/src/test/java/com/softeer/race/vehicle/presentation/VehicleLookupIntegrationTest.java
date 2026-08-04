package com.softeer.race.vehicle.presentation;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 차량 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 비회원 조회
 * 쿠키 없이 통과하는지. 시세 조회의 앞단이라 비회원이 호출해야 한다
 * <p>
 * 2. 이미지 없는 차량
 * 대표 이미지 없는 경로가 실제로 동작하는지
 * <p>
 * 3. 공백 처리
 * 소유자명 앞뒤 공백을 다듬어 조회하는지
 * <p>
 * 4. 실패 응답 동일성
 * 미등록 번호판과 소유자명 불일치가 완전히 같은 응답인지
 * <p>
 * 5. 비노출
 * 기준가와 소유자명이 응답에 실리지 않는지
 * <p>
 * Clock 을 고정하지 않는다. 시각에 의존하는 계산이 없어 고정할 이유가 없다 — 시세를 산정하지 않기
 * 때문이고, 그래서 시세 조회 통합테스트와 달리 해가 바뀌어도 값이 흔들리지 않는다.
 * <p>
 * 차량 카탈로그는 시세 조회·판매 신청과 같은 픽스처를 쓴다. 따로 시드하면 두 API 가 서로 다른 차를
 * 보게 되어 "조회한 차로 시세를 받는다"를 테스트가 더 이상 보증하지 못한다.
 */
@DisplayName("차량 조회 통합 테스트")
@Transactional
@Sql("/sql/vehicle-catalog-fixture.sql")
class VehicleLookupIntegrationTest extends IntegrationTestSupport {

    private static final String PLATE_WITH_IMAGE = "12가3456";
    private static final String OWNER_WITH_IMAGE = "김민수";
    private static final String PLATE_WITHOUT_IMAGE = "90마5678";
    private static final String OWNER_WITHOUT_IMAGE = "정하늘";
    private static final String IMAGE_URL = "https://cdn.race.dev/vehicles/grandeur-ig.jpg";

    /** 픽스처 201번의 기준가. 그 모델의 신차급 가격이라 응답으로 나가면 안 되는 값이다 */
    private static final long CATALOG_BASE_PRICE = 34_000_000L;

    // 핸들러에 @LoginUser 를 붙이면 이 시나리오만 깨진다. 비회원용이라는 결정을 고정하는 유일한 자리다
    @Test
    @DisplayName("시나리오 1 : 세션 쿠키 없이 조회하면 200과 제원을 준다")
    void scenario1_WorksWithoutAuthentication() throws Exception {
        lookup(PLATE_WITH_IMAGE, OWNER_WITH_IMAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value(PLATE_WITH_IMAGE))
                .andExpect(jsonPath("$.manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$.model").value("그랜저 IG"))
                .andExpect(jsonPath("$.modelYear").value(2021))
                .andExpect(jsonPath("$.fuelType").value("GASOLINE"))
                .andExpect(jsonPath("$.transmission").value("AUTOMATIC"))
                .andExpect(jsonPath("$.mainImageUrl").value(IMAGE_URL));
    }

    // 조회만 하고 아무것도 만들지 않는다는 것을 못 박는다
    @Test
    @DisplayName("시나리오 2 : 조회는 차량도 신청도 만들지 않는다")
    void scenario2_CreatesNothing() throws Exception {
        lookup(PLATE_WITH_IMAGE, OWNER_WITH_IMAGE).andExpect(status().isOk());

        assertThat(countOf("vehicle")).isZero();
        assertThat(countOf("evaluation")).isZero();
        assertThat(countOf("auction")).isZero();
    }

    @Test
    @DisplayName("시나리오 3 : 대표 이미지가 없는 차량도 조회된다")
    void scenario3_VehicleWithoutImage() throws Exception {
        lookup(PLATE_WITHOUT_IMAGE, OWNER_WITHOUT_IMAGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("520i"))
                // 화면이 대체 이미지를 고르면 된다, 이미지가 없다고 조회가 막히지는 않는다
                .andExpect(jsonPath("$.mainImageUrl").value((Object) null));
    }

    @Test
    @DisplayName("시나리오 4 : 소유자명 앞뒤 공백은 다듬어 조회한다")
    void scenario4_TrimsOwnerName() throws Exception {
        // 공백 하나로 조회 실패를 주면 사용자가 원인을 알 수 없다
        lookup(PLATE_WITH_IMAGE, "  김민수  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("그랜저 IG"));
    }

    // 두 응답이 갈라지면 번호판을 바꿔 넣어보며 소유자명을 역추적할 수 있다
    @Test
    @DisplayName("시나리오 5 : 미등록 번호판과 소유자명 불일치가 완전히 같은 응답이다")
    void scenario5_IndistinguishableFailures() throws Exception {
        String unregistered = bodyOf(lookup("99저9999", "김민수")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_SPEC_NOT_FOUND")));

        String wrongOwner = bodyOf(lookup(PLATE_WITH_IMAGE, "남의이름")
                .andExpect(status().isNotFound()));

        assertThat(wrongOwner).isEqualTo(unregistered);
    }

    @Test
    @DisplayName("시나리오 6 : 응답에 기준가와 소유자명이 실리지 않는다")
    void scenario6_HidesBasePriceAndOwnerName() throws Exception {
        String body = bodyOf(lookup(PLATE_WITH_IMAGE, OWNER_WITH_IMAGE).andExpect(status().isOk()));

        // 응답 DTO 에 필드가 하나 늘는 것만으로 무너지므로 문자열로 못 박는다
        assertThat(body)
                .doesNotContain(String.valueOf(CATALOG_BASE_PRICE))
                .doesNotContain(OWNER_WITH_IMAGE)
                .doesNotContain("estimatedPrice")
                .doesNotContain("mileage");
    }

    @Test
    @DisplayName("시나리오 7 : 형식에 맞지 않는 번호판은 400이다")
    void scenario7_InvalidPlateNumberFormat() throws Exception {
        lookup("", OWNER_WITH_IMAGE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 정규화하지 않기로 했으므로 공백·대시가 섞인 값도 여기서 막힌다
        lookup("12가 3456", OWNER_WITH_IMAGE).andExpect(status().isBadRequest());
        lookup("12-가-3456", OWNER_WITH_IMAGE).andExpect(status().isBadRequest());
        lookup(PLATE_WITH_IMAGE, "   ").andExpect(status().isBadRequest());
    }

    // ================= 요청 =================

    private ResultActions lookup(String plateNumber, String ownerName) throws Exception {
        return mockMvc.perform(post("/api/vehicles/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"plateNumber": "%s", "ownerName": "%s"}
                        """.formatted(plateNumber, ownerName)));
    }

    private static String bodyOf(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return result.getResponse().getContentAsString();
    }

    private Integer countOf(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Integer.class);
    }
}
