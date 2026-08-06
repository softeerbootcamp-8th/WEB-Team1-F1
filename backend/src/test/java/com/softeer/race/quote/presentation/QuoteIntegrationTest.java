package com.softeer.race.quote.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비회원 시세 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 조회와 산정
 * 카탈로그의 제원으로 예상 시세를 계산해 함께 내려주는지
 * <p>
 * 2. 소유자명 역추적 차단
 * 미등록 번호판과 소유자명 불일치가 구분 불가능한 같은 응답인지. 이게 갈라지면 번호판을 바꿔
 * 넣어보며 소유자명을 알아낼 수 있다
 * <p>
 * 3. 기준가 비노출
 * 조회기가 준 기준가가 응답에 실리지 않는지. 실리면 예상 시세와 나란히 놓고 감가율이 역산된다
 * <p>
 * 4. 요청 검증
 * 번호판 형식과 소유자명 누락을 400 으로 막는지
 */
@DisplayName("예상 시세 조회 통합 테스트")
@Transactional
@Sql("/sql/vehicle-catalog-fixture.sql")
class QuoteIntegrationTest extends IntegrationTestSupport {

    // 픽스처의 예상 시세가 이 연도(2026)를 기준으로 계산돼 있다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0, 0);

    /**
     * 주행거리는 카탈로그가 아니라 요청에서 온다. 금액을 검증하는 시나리오는 이 값을 직접 넘기고,
     * 나머지는 기본값을 쓴다 — 예전처럼 픽스처의 주행거리에 기대면 값의 출처가 보이지 않는다.
     */
    private static final int DEFAULT_MILEAGE = 45_000;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    // ================= 조회와 산정 =================

    @Test
    @DisplayName("번호판과 소유자명이 맞으면 제원과 예상 시세를 준다")
    void returnsSpecAndEstimate() throws Exception {
        // when : 로그인 없이 호출한다
        ResultActions response = request("12가3456", "김민수");

        // then 1 : 기준가 3400만에서 5년·신고한 4.5만km 감가
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedPrice").value(23_200_000L));

        // then 2 : 화면이 필요한 제원
        response.andExpect(jsonPath("$.plateNumber").value("12가3456"))
                .andExpect(jsonPath("$.manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$.model").value("그랜저 IG"))
                .andExpect(jsonPath("$.modelYear").value(2021))
                // 신고한 주행거리를 그대로 되돌려준다, 화면이 "이 값으로 계산된 시세"임을 보여야 한다
                .andExpect(jsonPath("$.mileage").value(DEFAULT_MILEAGE))
                .andExpect(jsonPath("$.fuelType").value("GASOLINE"))
                .andExpect(jsonPath("$.transmission").value("AUTOMATIC"))
                .andExpect(jsonPath("$.mainImageUrl")
                        .value("https://cdn.race.dev/vehicles/grandeur-ig.jpg"));
    }

    @Test
    @DisplayName("대표 이미지가 없는 차량도 조회된다")
    void handlesMissingImage() throws Exception {
        // given : 203번은 대표 이미지가 null 이다

        // when
        ResultActions response = request("90마5678", "정하늘", 61_000);

        // then : 이미지가 없다고 조회가 막히지는 않는다, 화면이 대체 이미지를 고르면 된다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImageUrl").value((Object) null))
                .andExpect(jsonPath("$.estimatedPrice").value(41_370_000L));
    }

    @Test
    @DisplayName("감가가 기준가를 넘기는 차량은 하한선으로 막힌다")
    void flooredEstimate() throws Exception {
        // given : 204번은 2010년식이고 32만km 를 신고하면 감가 합계가 기준가 1800만을 넘긴다

        // when
        ResultActions response = request("24바1234", "오래된", 320_000);

        // then : 음수가 아니라 기준가의 20%
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedPrice").value(3_600_000L));
    }

    @Test
    @DisplayName("소유자명 앞뒤 공백은 다듬어 조회한다")
    void trimsOwnerName() throws Exception {
        // when : 사용자가 실수로 공백을 붙여 보낸다
        ResultActions response = request("12가3456", "  김민수  ");

        // then : 공백 하나로 조회 실패를 주면 사용자가 원인을 알 수 없다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedPrice").value(23_200_000L));
    }

    // ================= 소유자명 역추적 차단 =================

    @Test
    @DisplayName("미등록 번호판과 소유자명 불일치가 완전히 같은 응답이다")
    void indistinguishableFailures() throws Exception {
        // given : 하나는 없는 번호판, 하나는 있는 번호판에 틀린 소유자명

        // when
        String unregistered = request("99저9999", "김민수")
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String wrongOwner = request("12가3456", "남의이름")
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // then : 코드도 메시지도 갈라지지 않아야 번호판 대입으로 소유자명을 알아낼 수 없다
        assertThat(wrongOwner).isEqualTo(unregistered);
    }

    @Test
    @DisplayName("조회 실패는 어느 쪽이 틀렸는지 알려주지 않는다")
    void failureMessageDoesNotLeakReason() throws Exception {
        // when
        ResultActions response = request("12가3456", "남의이름");

        // then : 메시지에 "소유자명이 다릅니다" 같은 단서가 없다
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("차량 정보를 찾을 수 없습니다. 번호판과 이름을 확인해 주세요."));
    }

    // ================= 기준가 비노출 =================

    // 응답 DTO에 필드를 추가하는 것만으로 이 보호가 무너지므로 테스트로 못 박는다
    @Test
    @DisplayName("응답에 기준가와 소유자명이 실리지 않는다")
    void doesNotExposeBasePriceOrOwnerName() throws Exception {
        // when
        ResultActions response = request("12가3456", "김민수");

        // then 1 : 기준가가 실리면 예상 시세와 나란히 놓고 감가율을 역산할 수 있다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice").doesNotExist());

        // then 2 : 소유자명은 호출자가 방금 보낸 값이라 되돌려줄 이유가 없다
        response.andExpect(jsonPath("$.ownerName").doesNotExist());
    }

    // ================= 요청 검증 =================

    @Test
    @DisplayName("번호판에 공백이나 대시가 섞이면 400 이다")
    void rejectsUnnormalizedPlateNumber() throws Exception {
        // given : 조회 구현체가 정규화하지 않으므로 여기서 막아야 한다

        // when & then
        request("12가 3456", "김민수").andExpect(status().isBadRequest());
        request("12-가-3456", "김민수").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("소유자명이 비면 400 이다")
    void rejectsBlankOwnerName() throws Exception {
        // given : 소유자명 없이 조회되면 번호판만으로 제원이 나가는 것과 같다

        // when & then
        request("12가3456", "   ").andExpect(status().isBadRequest());
    }

    /** 기본 주행거리로 조회한다. 금액을 검증하지 않는 시나리오는 이 값이 무엇이든 무관하다 */
    private ResultActions request(String plateNumber, String ownerName) throws Exception {
        return request(plateNumber, ownerName, DEFAULT_MILEAGE);
    }

    private ResultActions request(String plateNumber, String ownerName, int mileage)
            throws Exception {
        return mockMvc.perform(post("/api/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"plateNumber": "%s", "ownerName": "%s", "mileage": %d}
                        """.formatted(plateNumber, ownerName, mileage)));
    }
}
