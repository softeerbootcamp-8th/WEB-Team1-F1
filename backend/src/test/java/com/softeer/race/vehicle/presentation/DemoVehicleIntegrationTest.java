package com.softeer.race.vehicle.presentation;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 차량 안내를 컨트롤러에서 DB까지
 * <p>
 * 1. 비회원 조회
 * 쿠키 없이 통과하는지. 안내가 필요한 사람이 안내를 볼 수 있어야 한다는 결정을 고정하는 자리다
 * <p>
 * 2. 진행 중 제외
 * REQUESTED 와 APPROVED 가 빠지고 REJECTED 는 남는지. 안내와 신청이 같은 기준을 쓰는지가 여기서 갈린다
 * <p>
 * 3. 상한 10
 * 걸러낸 뒤에 자르는지. 자르기 전에 걸면 응답이 10대보다 적어진다
 * <p>
 * 4. 소진 큐
 * id 오름차순이라 앞의 차가 신청되면 잘려 있던 다음 차가 올라오는지
 * <p>
 * 5. 빈 목록
 * 쓸 수 있는 차가 없을 때 예외가 아니라 200 과 빈 배열인지
 * <p>
 * 6. 비노출
 * 기준가와 대표 이미지가 응답에 실리지 않는지
 * <p>
 * Clock 을 고정하지 않는다. 이 안내는 시각을 읽지 않는다 — 진행 중 판정은 상태로만 하고,
 * 정렬도 상한도 시각과 무관하다.
 * <p>
 * 차량 카탈로그를 시세 조회와 같은 픽스처로 쓰지 않는다. 그쪽은 기준가와 감가 계산이 값으로
 * 검증되고 있어, 상한을 넘기려고 차를 더 넣으면 그 테스트가 세는 건수가 함께 흔들린다.
 */
@DisplayName("데모 차량 안내 통합 테스트")
@Transactional
@Sql("/sql/demo-vehicle-fixture.sql")
class DemoVehicleIntegrationTest extends IntegrationTestSupport {

    /** 진행 중(REQUESTED) 신청이 걸린 차량 */
    private static final String REQUESTED_PLATE = "11가1101";
    /** 진단이 끝났지만(APPROVED) 아직 종료되지 않은 차량 */
    private static final String APPROVED_PLATE = "11가1102";
    /** 반려(REJECTED)된 차량. 다시 신청할 수 있어 목록의 첫 번째다 */
    private static final String REJECTED_PLATE = "11가1103";
    /** 남는 11대 중 열 번째. 응답의 마지막 항목이다 */
    private static final String LAST_SHOWN_PLATE = "11가1112";
    /** 상한에 걸려 잘리는 열한 번째 */
    private static final String TRUNCATED_PLATE = "11가1113";

    // 핸들러에 @LoginUser 를 붙이면 이 시나리오만 깨진다. 비회원용이라는 결정을 고정하는 유일한 자리다
    @Test
    @DisplayName("시나리오 1 : 세션 쿠키 없이 조회하면 200과 데모 차량 다섯 칸을 준다")
    void scenario1_WorksWithoutAuthentication() throws Exception {
        demoVehicles()
                .andExpect(status().isOk())
                // 반려된 1103이 첫 항목이다. id 오름차순인데 앞의 둘이 진행 중이라 빠졌다
                .andExpect(jsonPath("$[0].plateNumber").value(REJECTED_PLATE))
                // 소유자명을 내려준다. 차량 조회 응답과 반대인데, 무엇을 입력해야 하는지 알려주는 것이 목적이다
                .andExpect(jsonPath("$[0].ownerName").value("반려삼"))
                .andExpect(jsonPath("$[0].manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$[0].model").value("쏘나타 DN8"))
                .andExpect(jsonPath("$[0].modelYear").value(2020));
    }

    @Test
    @DisplayName("시나리오 2 : 진행 중인 신청이 걸린 차량은 빠지고 반려된 차량은 남는다")
    void scenario2_ExcludesInProgressOnly() throws Exception {
        demoVehicles()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.plateNumber == '" + REQUESTED_PLATE + "')]").isEmpty())
                // APPROVED 가 빠지는 것이 이 시나리오의 핵심이다. 진단이 끝나도 출품 동의가 남아
                // 흐름이 계속되므로, 안내하면 넣는 순간 중복으로 거절된다
                .andExpect(jsonPath("$[?(@.plateNumber == '" + APPROVED_PLATE + "')]").isEmpty())
                // 반려는 종료 상태다. 빠지면 다시 신청할 수 있는 차를 안내하지 않는 것이 된다
                .andExpect(jsonPath("$[?(@.plateNumber == '" + REJECTED_PLATE + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("시나리오 3 : 13대 중 2대가 진행 중이어도 상한만큼 10대를 준다")
    void scenario3_LimitsAfterFiltering() throws Exception {
        // 자르기 전에 상한을 걸면 여기가 8이 된다. 가장 조용히 틀리는 지점이라 건수를 직접 단언한다
        demoVehicles()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[9].plateNumber").value(LAST_SHOWN_PLATE))
                .andExpect(jsonPath("$[?(@.plateNumber == '" + TRUNCATED_PLATE + "')]").isEmpty());
    }

    @Test
    @DisplayName("시나리오 4 : 앞의 차량이 신청되면 잘려 있던 다음 차량이 올라온다")
    void scenario4_RefillsFromTheTail() throws Exception {
        requestVisitQuoteFor(REJECTED_PLATE);

        // 목록이 "아직 안 쓴 가장 오래된 10대"라는 뜻이다. 정렬이 없으면 무엇이 올라올지 정해지지 않고,
        // 카탈로그가 만 건이 되어도 앞에서부터 소진되며 자동으로 뒤가 채워진다
        demoVehicles()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[?(@.plateNumber == '" + REJECTED_PLATE + "')]").isEmpty())
                .andExpect(jsonPath("$[9].plateNumber").value(TRUNCATED_PLATE));
    }

    @Test
    @DisplayName("시나리오 5 : 쓸 수 있는 차량이 없으면 200과 빈 배열이다")
    void scenario5_ReturnsEmptyList() throws Exception {
        jdbcTemplate.execute("delete from vehicle_catalog");

        // 404 나 204 가 아니다. "안내할 것이 없다"는 오류가 아니라 정상적인 상태이고,
        // 화면은 목록이 비었을 때 도움말을 접기만 하면 된다
        demoVehicles()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("시나리오 6 : 기준가와 대표 이미지는 응답에 실리지 않는다")
    void scenario6_HidesBasePriceAndImage() throws Exception {
        demoVehicles()
                .andExpect(status().isOk())
                // 기준가가 예상 시세와 나란히 놓이면 감가율이 역산된다. 카탈로그 엔티티를 그대로
                // 내보내면 바로 생기는 구멍이라, 프로젝션이 다섯 칸만 뽑는지를 여기서 잠근다
                .andExpect(jsonPath("$[0].basePrice").doesNotExist())
                .andExpect(jsonPath("$[0].mainImageUrl").doesNotExist());
    }

    private ResultActions demoVehicles() throws Exception {
        return mockMvc.perform(get("/api/vehicles/demo"));
    }

    /**
     * 그 번호판으로 진행 중인 방문견적 신청을 만든다.
     * <p>
     * 접수 API 를 부르지 않고 직접 심는다. 그쪽은 로그인과 방문일 규칙을 요구해 이 시나리오가
     * 확인하려는 것(목록이 다시 채워지는가)과 무관한 조건이 딸려 붙는다.
     */
    private void requestVisitQuoteFor(String plateNumber) {
        jdbcTemplate.update("""
                insert into vehicle (seller_id, manufacturer, model, model_year, mileage, fuel_type,
                                     transmission, plate_number, estimated_price, created_at, updated_at)
                values (1100, 'HYUNDAI', '쏘나타 DN8', 2020, null, 'GASOLINE', 'AUTOMATIC',
                        ?, null, NOW(6), NOW(6))
                """, plateNumber);

        jdbcTemplate.update("""
                insert into evaluation (vehicle_id, evaluator_id, visit_date, visit_address, contact_phone,
                                        status, reject_reason, created_at, updated_at)
                select id, null, '2026-08-24', '서울 중구 세종대로 110', '01011111199',
                       'REQUESTED', null, NOW(6), NOW(6)
                from vehicle
                where plate_number = ?
                order by id desc
                limit 1
                """, plateNumber);
    }
}
