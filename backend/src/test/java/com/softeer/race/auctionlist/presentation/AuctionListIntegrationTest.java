package com.softeer.race.auctionlist.presentation;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매글 목록 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 그룹 정렬
 * 진행중 → 예정 → 종료, 그룹 안에서는 임박순. 종료만 최근에 끝난 것부터
 * <p>
 * 2. 커서 왕복
 * 응답의 nextCursor 를 쿼리 파라미터로 돌려보내면 이어 읽히는지
 * <p>
 * 3. 커서 검증
 * 일부만 보내거나 알 수 없는 그룹 순번이면 400. 이 방어가 없으면 언박싱에서 500 이 난다
 * <p>
 * 4. 직렬화
 * 남은 시간을 내리지 않고 절대 시각과 서버 시각을 오프셋 없는 KST 문자열로 준다
 * <p>
 * 5. 차량 조건 필터
 * 쿼리 파라미터가 조건으로 걸리는지, 잘못된 값은 400 인지
 */
@DisplayName("경매글 목록 조회 통합 테스트")
@Transactional
@Sql("/sql/auction-list-fixture.sql")
class AuctionListIntegrationTest extends IntegrationTestSupport {

    // 픽스처가 이 시각을 기준으로 진행중 101·102·103·110 / 예정 104·105 / 종료 106·107 로 나뉜다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0, 0);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    // ================= 첫 페이지 =================

    @Test
    @DisplayName("커서 없이 요청하면 첫 페이지를 진행중 → 예정 → 종료 순으로 준다")
    void firstPage() throws Exception {
        // when : 파라미터 없이 요청하면 첫 페이지다
        ResultActions response = mockMvc.perform(get("/api/auctions"));

        // then 1 : 삭제(108번)와 임시저장(109번)을 뺀 여덟 건
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(8));

        // then 2 : 진행중은 마감 임박순, 예정은 시작 임박순, 종료는 최근에 끝난 것부터
        response.andExpect(jsonPath("$.content[*].auctionId")
                .value(org.hamcrest.Matchers.contains(101, 102, 103, 110, 104, 105, 107, 106)));

        // then 3 : PAGE_SIZE(20)에 못 미치므로 다음 페이지가 없다
        response.andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        // then 4 : 오프셋 없는 KST 문자열
        response.andExpect(jsonPath("$.serverTime").value("2026-08-03T12:00:00"));
    }

    @Test
    @DisplayName("카드에 화면이 필요한 값이 모두 담긴다")
    void cardFields() throws Exception {
        // when
        ResultActions response = mockMvc.perform(get("/api/auctions"));

        // then 1 : 상태가 아니라 시각으로 판정한 단계
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].auctionId").value(101))
                .andExpect(jsonPath("$.content[0].phase").value("LIVE"));

        // then 2 : 경매글과 차량에서. 제조사는 표시 문구가 아니라 코드로 내리고 문구는 화면이 매핑한다
        response.andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://cdn.race.dev/101.jpg"))
                .andExpect(jsonPath("$.content[0].manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$.content[0].model").value("아반떼 CN7"))
                .andExpect(jsonPath("$.content[0].modelYear").value(2022))
                .andExpect(jsonPath("$.content[0].mileage").value(35000));

        // then 2-1 : 키워드는 저장 순서(GOOD_TIRE 부터)가 아니라 선언 순서다
        response.andExpect(jsonPath("$.content[0].keywords")
                .value(org.hamcrest.Matchers.contains("ACCIDENT_FREE", "NO_LEAK", "GOOD_TIRE")));

        // then 3 : 남은 초가 아니라 절대 시각을 준다. 카운트다운은 화면이 돌린다
        response.andExpect(jsonPath("$.content[0].openAt").value("2026-08-03T11:20:00"))
                .andExpect(jsonPath("$.content[0].startAt").value("2026-08-03T11:50:00"))
                .andExpect(jsonPath("$.content[0].endAt").value("2026-08-03T12:10:00"));

        // then 4 : 시작가와 현재가
        response.andExpect(jsonPath("$.content[0].startPrice").value(10000000))
                .andExpect(jsonPath("$.content[0].currentPrice").value(11000000));
    }

    @Test
    @DisplayName("입찰이 없는 경매는 현재가가 시작가로 채워진다")
    void currentPriceFallsBackToStartPrice() throws Exception {
        // given : 102번은 아직 입찰이 없다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions"));

        // then : null 처리를 화면에 떠넘기지 않는다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].auctionId").value(102))
                .andExpect(jsonPath("$.content[1].startPrice").value(38000000))
                .andExpect(jsonPath("$.content[1].currentPrice").value(38000000));
    }

    @Test
    @DisplayName("키워드가 매겨지지 않은 차량은 null 이 아니라 빈 배열로 나간다")
    void keywordlessCardCarriesEmptyArray() throws Exception {
        // given : 102번 차량에는 키워드가 없다. 평가를 거치지 않고 출품된 차가 그렇다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions"));

        // then : null 이면 화면이 분기를 하나 더 진다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].auctionId").value(102))
                .andExpect(jsonPath("$.content[1].keywords").isArray())
                .andExpect(jsonPath("$.content[1].keywords").isEmpty());
    }

    @Test
    @DisplayName("예정 카드는 아직 시작 전이라 대기 단계로 나간다")
    void pendingCardPhase() throws Exception {
        // when
        ResultActions response = mockMvc.perform(get("/api/auctions"));

        // then : 104번은 방이 열렸지만(개장 12:00) 시작(12:30) 전이다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[4].auctionId").value(104))
                .andExpect(jsonPath("$.content[4].phase").value("WAITING"));
    }

    // ================= 커서 왕복 =================

    @Test
    @DisplayName("커서를 돌려보내면 그 지점부터 이어 읽는다")
    void resumesFromCursor() throws Exception {
        // given : 진행중 103번(마감 12:15)까지 읽은 상태의 커서
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "1")
                .param("sortAt", "2026-08-03T12:15:00")
                .param("auctionId", "103"));

        // then : 진행중 나머지(110번) → 예정 처음부터 → 종료 처음부터
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[*].auctionId")
                        .value(org.hamcrest.Matchers.contains(110, 104, 105, 107, 106)));
    }

    @Test
    @DisplayName("종료 그룹 커서는 최근에 끝난 것 다음부터 이어 읽는다")
    void resumesWithinEndedGroup() throws Exception {
        // given : 종료 107번(마감 11:20)까지 읽은 상태

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "3")
                .param("sortAt", "2026-08-03T11:20:00")
                .param("auctionId", "107"));

        // then : 내림차순이라 더 먼저 끝난 106번만 남는다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].auctionId").value(106))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ================= 커서 검증 =================

    @Test
    @DisplayName("커서를 일부만 보내면 400 이다")
    void rejectsPartialCursor() throws Exception {
        // given : sortPriority 와 sortAt 이 빠졌다
        // 검증이 안 걸리면 Integer 를 int 로 언박싱하다 500 이 난다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("auctionId", "103"));

        // then : 조용히 첫 페이지로 돌리면 무한 스크롤이 처음으로 되감겨 더 헷갈린다
        response.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("알 수 없는 그룹 순번이면 400 이다")
    void rejectsUnknownGroupOrder() throws Exception {
        // given : 그룹은 1·2·3 뿐이다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "9")
                .param("sortAt", "2026-08-03T12:15:00")
                .param("auctionId", "103"));

        // then : 어느 그룹부터 읽을지 정할 수 없다
        response.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 탭의 커서를 필터와 함께 보내면 400 이다")
    void rejectsCursorFromDifferentGroup() throws Exception {
        // given : 예정(2) 커서를 진행중 필터와 함께 보낸다. 탭을 옮기며 커서를 안 버린 경우다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("filter", "LIVE")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "2")
                .param("sortAt", "2026-08-03T12:30:00")
                .param("auctionId", "104"));

        // then : 진행중만 읽으므로 예정 커서는 버려진다. 조용히 첫 페이지를 주면 화면이 되감긴다
        response.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("같은 그룹 커서는 필터와 함께 통과하고 그 그룹 안에서만 이어 읽는다")
    void resumesWithinFilteredGroup() throws Exception {
        // given : 진행중 103번(마감 12:15)까지 읽은 커서를 진행중 필터와 함께 보낸다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("filter", "LIVE")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "1")
                .param("sortAt", "2026-08-03T12:15:00")
                .param("auctionId", "103"));

        // then : 필터가 없으면 110 뒤에 예정·종료가 붙는다(resumesFromCursor).
        // 필터를 거부하기만 하는 검증으로는 이 경로가 통째로 막히므로 성공 사례도 함께 잡아둔다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].auctionId").value(110))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("필터 없이 보낸 커서는 어느 그룹이든 통과한다")
    void acceptsAnyGroupCursorWithoutFilter() throws Exception {
        // given : 전체 탭은 그룹을 넘나들며 읽으므로 예정 커서가 정상이다

        // when
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("snapshotAt", "2026-08-03T12:00:00")
                .param("sortPriority", "2")
                .param("sortAt", "2026-08-03T12:30:00")
                .param("auctionId", "104"));

        // then
        response.andExpect(status().isOk());
    }

    // ================= 차량 조건 필터 =================

    @Test
    @DisplayName("제조사 필터는 해당 제조사의 경매만 준다")
    void filtersByManufacturer() throws Exception {
        // when : 기아는 진행중 102(쏘렌토)·103(EV6)뿐이다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("manufacturer", "KIA"));

        // then : 그룹 순서와 정렬은 필터와 무관하게 유지된다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].auctionId")
                        .value(org.hamcrest.Matchers.contains(102, 103)));
    }

    @Test
    @DisplayName("연료 필터는 반복 파라미터로 여러 개 받고 그룹 경계를 넘어도 걸린다")
    void filtersByRepeatedFuelParamsAcrossGroups() throws Exception {
        // when : 하이브리드·전기는 진행중 102·103 과 종료 107·106 에 걸쳐 있다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("fuelTypes", "HYBRID")
                .param("fuelTypes", "ELECTRIC"));

        // then : 예정(가솔린뿐)은 건너뛰고 종료는 최근에 끝난 순서다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].auctionId")
                        .value(org.hamcrest.Matchers.contains(102, 103, 107, 106)));
    }

    @Test
    @DisplayName("주행거리 상한을 걸면 그 이하의 차량만 준다")
    void filtersByMileageMax() throws Exception {
        // when : 30000km 이하는 902(18450)·903(27800)이다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("mileageMax", "30000"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].auctionId")
                        .value(org.hamcrest.Matchers.contains(102, 103)));
    }

    @Test
    @DisplayName("가격 상한은 그룹마다 화면에 보이는 가격 기준으로 걸린다")
    void filtersByPriceMaxAcrossGroups() throws Exception {
        // when : 3400만 이하는 진행중 101(1100만)·103(3400만), 예정 105(2900만), 종료 107(2200만)이다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("priceMax", "34000000"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].auctionId")
                        .value(org.hamcrest.Matchers.contains(101, 103, 105, 107)));
    }

    @Test
    @DisplayName("알 수 없는 제조사 값이면 400 이다")
    void rejectsUnknownManufacturer() throws Exception {
        // when : 조용히 무시하면 전체 목록이 내려가 필터가 안 걸린 줄 모른다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("manufacturer", "HYUNDAY"));

        // then
        response.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주행거리 범위가 뒤집혀 있으면 400 이다")
    void rejectsInvertedMileageRange() throws Exception {
        // when : 뒤집힌 범위는 항상 빈 결과라 실수일 수밖에 없다
        ResultActions response = mockMvc.perform(get("/api/auctions")
                .param("mileageMin", "50000")
                .param("mileageMax", "10000"));

        // then
        response.andExpect(status().isBadRequest());
    }
}
