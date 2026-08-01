package com.softeer.race.auctionroom.presentation;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매방 현황 구독을 컨트롤러에서 DB까지
 * <p>
 * 단위테스트로는 볼 수 없는 것만 여기서 고정한다. 응답이 끝나지 않고 열린 채로 남는지,
 * 미디어타입과 이벤트 프레이밍이 맞는지, 그리고 다른 사람이 들어왔을 때 이미 열려 있던 연결로
 * 새 현황이 흘러 들어가는지다. 셋 다 객체를 돌려받아서는 알 수 없다.
 */
@DisplayName("경매방 현황 구독 통합 테스트")
class AuctionRoomStreamIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    private static final long LIVE_AUCTION_ID = 4L;
    private static final long CLOSED_AUCTION_ID = 2L;
    private static final long MISSING_AUCTION_ID = 999L;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 진행 중 경매방 구독 -> 연결이 열린 채 첫 현황이 오고, 다음 사람이 들어오면 이미 열린 연결로도 흘러 들어간다")
    @Sql("/sql/auction-room-stream.sql")
    void scenario1_Subscribe_ReceivesStateAndLaterBroadcast() throws Exception {
        // when : 첫 사람이 구독
        MvcResult first = subscribe(LIVE_AUCTION_ID)
                .andExpectAll(
                        status().isOk(),
                        // then 1 : 응답이 끝나지 않고 비동기로 열린 채 남는다
                        request().asyncStarted(),
                        content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        // then 2 : 구독 직후 첫 현황이 한 번 온다, 아직 혼자라 접속자는 1이다
        String afterFirst = body(first);
        assertThat(afterFirst)
                .startsWith("data:")
                .contains("\"auctionId\":4")
                .contains("\"phase\":\"LIVE\"")
                .contains("\"currentPrice\":12500000")
                .contains("\"connectedCount\":1");

        // then 3 : 보는 사람을 가리지 않으므로 내 입찰 표시가 없고, 이름은 마스킹된 채로만 나간다
        assertThat(afterFirst)
                .contains("\"name\":\"김*현\"")
                .doesNotContain("\"mine\"")
                .doesNotContain("bidderId")
                .doesNotContain("김민현");

        // when : 두 번째 사람이 같은 방에 들어온다
        subscribe(LIVE_AUCTION_ID).andExpect(status().isOk());

        // then 4 : 먼저 열려 있던 연결로 늘어난 접속자 수가 흘러 들어간다, 다시 조회하지 않았는데 갱신된다
        assertThat(body(first))
                .contains("\"connectedCount\":2")
                .isNotEqualTo(afterFirst);
    }

    @Test
    @DisplayName("시나리오 2 : 완전히 닫힌 방 구독 -> 열어 둘 이유가 없으므로 거절한다")
    @Sql("/sql/auction-room-closed.sql")
    void scenario2_ClosedRoom_Rejected() throws Exception {
        // when : 마감 후 5분이 지난 방을 구독
        ResultActions response = subscribe(CLOSED_AUCTION_ID);

        // then : 자원이 없는 게 아니라 단계가 맞지 않는 것이라 404 가 아니라 409 다
        response.andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("ROOM_NOT_SUBSCRIBABLE"));
    }

    @Test
    @DisplayName("시나리오 3 : 없는 경매 구독 -> 구독을 만들지 않고 404 다")
    void scenario3_MissingAuction_NotFound() throws Exception {
        // when : 존재하지 않는 경매를 구독
        ResultActions response = subscribe(MISSING_AUCTION_ID);

        // then : 열 연결이 없으므로 404 다, 채널에도 아무 흔적이 남지 않는다
        response.andExpectAll(
                status().isNotFound(),
                jsonPath("$.code").value("AUCTION_ROOM_NOT_FOUND"));
    }

    // ================= 요청 ====================
    private ResultActions subscribe(long auctionId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId));
    }

    // text/event-stream 에는 charset 이 안 붙어 getContentAsString() 이 ISO-8859-1 로 떨어진다
    // SSE 명세가 이 미디어타입을 항상 UTF-8 로 디코딩하게 정하므로 여기서도 그렇게 읽는다
    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
