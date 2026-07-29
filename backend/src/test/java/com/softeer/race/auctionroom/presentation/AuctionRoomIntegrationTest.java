package com.softeer.race.auctionroom.presentation;

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
 * count(distinct bidder), 최신순, 이름 마스킹
 * <p>
 * 4. 직렬화
 * 마감 절대시각과 서버 시각을 오프셋 없는 KST 문자열로 내림
 */
@DisplayName("경매방 현황 조회 통합 테스트")
class AuctionRoomIntegrationTest extends IntegrationTestSupport {

    // 상수
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    private static final long LIVE_AUCTION_ID = 1L;
    private static final long CLOSED_AUCTION_ID = 2L;
    private static final long VIEWER_ID = 11L;

    // 접속자 맵은 컨텍스트에 살아 있는 싱글턴이라 시나리오마다 auctionId 를 다르게 써서 격리한다
    // 픽스처도 시나리오별로 나눠 arrange 가 서로 묶이지 않게 한다
    
    @TestBean(methodName = "fixedClock")
    private Clock clock;

    static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
    }


    @Test
    @DisplayName("시나리오 1 : 진행 중 경매방 조회 -> 마스킹된 최근 호가 + 입찰자 수 2명 + 접속자 본인 1명")
    @Sql("/sql/auction-room-live.sql")
    void scenario1_LiveRoom_HappyPath() throws Exception {
        // when : 입찰자 한 명이 방을 조회
        ResultActions response = getRoom(LIVE_AUCTION_ID, VIEWER_ID);

        // then 1 : 상태가 아니라 시각으로 판정한 단계
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LIVE"));

        // then 2 : 시작가와 현재가를 함께 내려 상승폭을 보인다
        response.andExpect(jsonPath("$.startPrice").value(10000000))
                .andExpect(jsonPath("$.currentPrice").value(12500000));

        // then 3 : 남은 초가 아니라 마감 절대시각과 서버 시각
        response.andExpect(jsonPath("$.endAt").value("2026-08-03T21:00:00"))
                .andExpect(jsonPath("$.serverTime").value("2026-08-03T20:45:12"));

        // then 4 : 세 건을 두 사람이 넣었으므로 입찰자 수는 2
        response.andExpect(jsonPath("$.bidderCount").value(2));

        // then 5 : 기록이 집계보다 먼저라 조회한 본인이 포함된다
        response.andExpect(jsonPath("$.connectedCount").value(1));

        // then 6 : 최신순, 이름은 앞뒤 한 글자만 남는다
        response.andExpect(jsonPath("$.recentBids.length()").value(3))
                .andExpect(jsonPath("$.recentBids[0].bidderName").value("김*현"))
                .andExpect(jsonPath("$.recentBids[0].amount").value(12500000))
                .andExpect(jsonPath("$.recentBids[0].bidAt").value("2026-08-03T20:44:31"))
                .andExpect(jsonPath("$.recentBids[1].bidderName").value("남**수"));
    }

    @Test
    @DisplayName("시나리오 2 : 완전 종료된 경매방 조회 -> 결과는 보이지만 접속자로 세지 않음")
    @Sql("/sql/auction-room-closed.sql")
    void scenario2_ClosedRoom_NotCountedAsPresence() throws Exception {
        // when : 마감 후 5분이 지난 방을 조회
        ResultActions response = getRoom(CLOSED_AUCTION_ID, VIEWER_ID);

        // then 1 : 없어진 리소스가 아니라 단계가 다를 뿐이라 200 이다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        // then 2 : 닫힌 단계에서는 조회해도 접속자로 세지 않는다
        response.andExpect(jsonPath("$.connectedCount").value(0));

        // then 3 : 낙찰 결과는 감출 정보가 아니므로 호가와 입찰자 수는 그대로 내려간다
        response.andExpect(jsonPath("$.bidderCount").value(1))
                .andExpect(jsonPath("$.recentBids.length()").value(1))
                .andExpect(jsonPath("$.recentBids[0].bidderName").value("이*호"));
    }

    // ================= 요청 ====================
    private ResultActions getRoom(long auctionId, long userId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                .header("X-User-Id", userId));
    }
}
