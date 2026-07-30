package com.softeer.race.auctionroom.presentation;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.softeer.race.support.IntegrationTestSupport;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매방 현황 상세 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 방 단계 판정
 * 개장·시작·마감 시각과 고정된 현재 시각으로 LIVE / CLOSED 판정
 * <p>
 * 2. 접속자 집계 (인메모리)
 * 조회가 곧 하트비트, 열린 단계에서만 집계
 * <p>
 * 3. 입찰 집계와 최근 호가 (MySQL)
 * 건수와 사람 수, 최신순, 이름 마스킹, 조회자 본인 호가 판정
 * <p>
 * 4. 한 번의 조회로 읽어오는 차량과 낙찰자 (MySQL)
 * 경매·경매글·차량·낙찰자를 프로젝션 하나로 받음
 * <p>
 * 5. 직렬화
 * 마감 절대시각과 서버 시각을 오프셋 없는 KST 문자열로 내림
 */
@DisplayName("경매방 현황 조회 통합 테스트")
class AuctionRoomIntegrationTest extends IntegrationTestSupport {

    // 상수
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    private static final long LIVE_AUCTION_ID = 1L;
    private static final long CLOSED_AUCTION_ID = 2L;
    private static final long DELETED_POST_AUCTION_ID = 3L;
    private static final long VIEWER_ID = 11L;
    private static final long WINNER_ID = 22L;

    // 접속자 맵은 컨텍스트에 살아 있는 싱글턴이라 시나리오마다 auctionId 를 다르게 써서 격리한다
    // 픽스처도 시나리오별로 나눠 arrange 가 서로 묶이지 않게 한다
    
    @TestBean(methodName = "fixedClock")
    private Clock clock;

    static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
    }


    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // 방 조회 한 번이 쓰는 쿼리 수를 세기 위해 켠다, 프로젝션을 엔티티 조회로 되돌리면 이 수가 늘어난다
    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @DisplayName("시나리오 1 : 진행 중 경매방 조회 -> 차량 요약 + 마스킹된 최근 호가에 내 입찰 표시 + 집계")
    @Sql("/sql/auction-room-live.sql")
    void scenario1_LiveRoom_HappyPath() throws Exception {
        // when : 입찰자 한 명이 방을 조회
        ResultActions response = getRoom(LIVE_AUCTION_ID, VIEWER_ID);

        // then 1 : 상태가 아니라 시각으로 판정한 단계
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.phase").value("LIVE"));

        // then 2 : 시작가와 현재가를 함께 내려 상승폭을 보인다
        response.andExpectAll(
                jsonPath("$.startPrice").value(10000000),
                jsonPath("$.currentPrice").value(12500000));

        // then 3 : 남은 초가 아니라 마감 절대시각과 서버 시각, 대기 화면을 위해 개장·시작 시각도 함께
        response.andExpectAll(
                jsonPath("$.openAt").value("2026-08-03T20:00:00"),
                jsonPath("$.startAt").value("2026-08-03T20:30:00"),
                jsonPath("$.endAt").value("2026-08-03T21:00:00"),
                jsonPath("$.serverTime").value("2026-08-03T20:45:12"));

        // then 4 : 목록을 거치지 않고 들어와도 화면을 채울 차량 정보가 함께 온다
        response.andExpectAll(
                jsonPath("$.vehicle.manufacturer").value("HYUNDAI"),
                jsonPath("$.vehicle.model").value("아반떼 CN7"),
                jsonPath("$.vehicle.modelYear").value(2022),
                jsonPath("$.vehicle.mileage").value(35000),
                jsonPath("$.vehicle.fuelType").value("GASOLINE"),
                jsonPath("$.thumbnailUrl").value("https://cdn.race.dev/avante-1.jpg"));

        // then 5 : 차량을 특정할 수 있는 번호판은 응답에 없다
        response.andExpect(jsonPath("$.vehicle.plateNumber").doesNotExist());

        // then 6 : 세 건을 두 사람이 넣었으므로 건수는 3이고 사람 수는 2다
        response.andExpectAll(
                jsonPath("$.bidCount").value(3),
                jsonPath("$.bidderCount").value(2));

        // then 7 : 기록이 집계보다 먼저라 조회한 본인이 포함된다
        response.andExpect(jsonPath("$.connectedCount").value(1));

        // then 8 : 최신순, 이름은 앞뒤 한 글자만 남는다
        response.andExpectAll(
                jsonPath("$.recentBids.length()").value(3),
                jsonPath("$.recentBids[0].name").value("김*현"),
                jsonPath("$.recentBids[0].amount").value(12500000),
                jsonPath("$.recentBids[0].bidAt").value("2026-08-03T20:44:31"),
                jsonPath("$.recentBids[1].name").value("남**수"));

        // then 9 : 조회자가 넣은 호가만 내 입찰로 표시된다, 마스킹된 이름으로는 구분할 수 없다
        response.andExpectAll(
                jsonPath("$.recentBids[0].mine").value(true),
                jsonPath("$.recentBids[1].mine").value(false),
                jsonPath("$.recentBids[2].mine").value(true));

        // then 10 : 역할은 값으로 내리고 배지 문구는 화면이 정한다
        response.andExpect(jsonPath("$.recentBids[0].role").value("DEALER"));

        // then 11 : 낙찰 확정 전이라 낙찰자가 없다
        response.andExpect(jsonPath("$.winner").isEmpty());

        // then 12 : 경매·집계·최근 호가 셋이면 충분하다, 2초 폴링이라 쿼리 수를 계약으로 고정한다
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("시나리오 2 : 완전 종료된 경매방 조회 -> 접속자로 세지 않고, 낙찰 여부는 조회자마다 다르다")
    @Sql("/sql/auction-room-closed.sql")
    void scenario2_ClosedRoom_NotCountedAsPresence() throws Exception {
        // when : 마감 후 5분이 지난 방을 낙찰자 본인이 조회
        ResultActions response = getRoom(CLOSED_AUCTION_ID, WINNER_ID);

        // then 1 : 없어진 리소스가 아니라 단계가 다를 뿐이라 200 이다
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.phase").value("CLOSED"));

        // then 2 : 닫힌 단계에서는 조회해도 접속자로 세지 않는다
        response.andExpect(jsonPath("$.connectedCount").value(0));

        // then 3 : 낙찰 결과는 감출 정보가 아니므로 호가와 집계는 그대로 내려간다
        response.andExpectAll(
                jsonPath("$.bidCount").value(1),
                jsonPath("$.bidderCount").value(1),
                jsonPath("$.recentBids.length()").value(1),
                jsonPath("$.recentBids[0].name").value("이*호"));

        // then 4 : 낙찰자도 호가와 같은 규칙으로 마스킹되고, 본인 여부는 이름 비교 없이 내려간다
        // then 5 : 낙찰자가 곧 유일한 입찰자라 자기 호가로도 표시된다
        response.andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(true),
                jsonPath("$.recentBids[0].mine").value(true));

        // then 6 : 같은 방을 남이 보면 이름은 같고 본인 표시만 꺼진다
        getRoom(CLOSED_AUCTION_ID, VIEWER_ID).andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.recentBids[0].mine").value(false));
    }

    @Test
    @DisplayName("시나리오 3 : 삭제된 경매글의 방 조회 -> 진행 중이어도 없는 방으로 취급한다")
    @Sql("/sql/auction-room-deleted-post.sql")
    void scenario3_DeletedPost_NotFound() throws Exception {
        // when : 글이 내려간 경매의 방을 조회
        ResultActions response = getRoom(DELETED_POST_AUCTION_ID, VIEWER_ID);

        // then : 도달할 수 없는 자원이므로 단계를 알리지 않고 404 다
        response.andExpectAll(
                status().isNotFound(),
                jsonPath("$.code").value("AUCTION_ROOM_NOT_FOUND"));
    }

    // ================= 요청 ====================
    private ResultActions getRoom(long auctionId, long userId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                .header("X-User-Id", userId));
    }
}
