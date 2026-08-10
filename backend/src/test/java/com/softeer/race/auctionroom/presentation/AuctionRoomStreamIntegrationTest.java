package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 경매방 현황 구독을 컨트롤러에서 DB까지
 * <p>
 * 단위테스트로는 볼 수 없는 것만 여기서 고정한다. 응답이 끝나지 않고 열린 채로 남는지,
 * 미디어타입과 이벤트 프레이밍이 맞는지, 그리고 다른 사람이 들어왔을 때 이미 열려 있던 연결로
 * 새 현황이 흘러 들어가는지다. 셋 다 객체를 돌려받아서는 알 수 없다.
 * <p>
 * 로그인 여부도 여기서 본다. 구독은 누구인지가 아니라 로그인했는지만 확인하므로 검증할 것은
 * 연결이 열리느냐 뿐이고, 그것 역시 응답 객체로는 알 수 없다.
 * <p>
 * 서버가 끊은 뒤 응답이 실제로 끝나는지, 두 번째로 디스패처를 통과하며 인증이 이미 나간 응답을 뒤집지 않는지
 */
@DisplayName("경매방 현황 구독 통합 테스트")
class AuctionRoomStreamIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RoomChannel roomChannel;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 연결 만료가 30분이라 대기 상한을 안 주면 끝나지 않은 응답을 30분 기다린다
    private static final long ASYNC_WAIT_MILLIS = 1_000L;

    // 세션 유효기간이 30분이다, 그보다 뒤로 밀면 구독이 열려 있는 사이 세션이 죽는다
    private static final long SESSION_EXPIRED_MINUTES = 31L;

    // 마감(시작 + 20분)에 결과 확인 5분까지 지난 시각이 되도록 뒤로 물린다
    private static final LocalDateTime CLOSED_START_AT = LocalDateTime.of(2026, 8, 3, 18, 30);

    private static final long MISSING_AUCTION_ID = 999L;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 진행 중 경매방 구독 -> 연결이 열린 채 첫 현황이 오고, 다음 사람이 들어오면 이미 열린 연결로도 흘러 들어간다")
    void subscribingReceivesStateAndLaterBroadcast() throws Exception {
        // given
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);

        // when : 첫 사람이 구독
        MvcResult first = subscribe(liveAuctionId)
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
                .contains("\"auctionId\":" + liveAuctionId)
                .contains("\"phase\":\"LIVE\"")
                .contains("\"currentPrice\":12500000")
                .contains("\"connectedCount\":1");

        // then 3 : 집계 둘이 방송에도 실린다, 한 사람이 두 번 넣었으므로 건수와 사람 수가 다르다
        assertThat(afterFirst)
                .contains("\"bidCount\":2")
                .contains("\"bidderCount\":1");

        // then 4 : 보는 사람을 가리지 않으므로 내 입찰 표시가 없고, 이름은 마스킹된 채로만 나간다
        assertThat(afterFirst)
                .contains("\"name\":\"김*현\"")
                .doesNotContain("\"mine\"")
                .doesNotContain("bidderId")
                .doesNotContain("viewerId")
                .doesNotContain("김민현");

        // when : 두 번째 사람이 같은 방에 들어온다
        subscribe(liveAuctionId).andExpect(status().isOk());

        // then 5 : 먼저 열려 있던 연결로 늘어난 접속자 수가 흘러 들어간다, 다시 조회하지 않았는데 갱신된다
        assertThat(body(first))
                .contains("\"connectedCount\":2")
                .isNotEqualTo(afterFirst);
    }

    @Test
    @DisplayName("시나리오 2 : 완전히 닫힌 방 구독 -> 열어 둘 이유가 없으므로 거절한다")
    void closedRoomIsRejected() throws Exception {
        // given : 마감 후 5분이 지난 방, 낙찰자는 이 판정과 무관하다
        long closedAuctionId = rooms.room(users.user("최판매", Role.GENERAL), CLOSED_START_AT).create();

        // when : 구독을 건다
        ResultActions response = subscribe(closedAuctionId);

        // then : 자원이 없는 게 아니라 단계가 맞지 않는 것이라 404 가 아니라 409 다
        // 기다렸다 다시 오면 되는 방이 아니라는 것까지 code 로 알린다
        response.andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("ROOM_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("시나리오 3 : 없는 경매 구독 -> 구독을 만들지 않고 404 다")
    void missingAuctionIsNotFound() throws Exception {
        // when : 존재하지 않는 경매를 구독
        ResultActions response = subscribe(MISSING_AUCTION_ID);

        // then : 열 연결이 없으므로 404 다, 채널에도 아무 흔적이 남지 않는다
        response.andExpectAll(
                status().isNotFound(),
                jsonPath("$.code").value("AUCTION_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 4 : 세션 없이 구독 -> 연결을 열지 않고 401")
    void sessionlessSubscribeIsUnauthorized() throws Exception {
        // given : 쿠키만 있으면 열렸을 진행 중인 방이다, 401 이 방 단계 때문이 아님을 분명히 한다
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);

        // when & then : 인터셉터에서 끊기므로 비동기 응답조차 시작되지 않는다
        subscribeWithoutSession(liveAuctionId).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_UNAUTHENTICATED"),
                request().asyncNotStarted());
    }

    @Test
    @DisplayName("시나리오 5 : 서버가 방을 끊음 -> 열려 있던 연결이 실제로 끝난다")
    void serverClosingRoomEndsConnection() throws Exception {
        // given : 진행 중인 방에 한 사람이 붙어 있다
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);
        MvcResult opened = subscribe(liveAuctionId).andExpect(request().asyncStarted()).andReturn();

        // when : 서버가 그 방을 끊는다
        roomChannel.closeRoom(liveAuctionId);

        // then : 열린 채로 남아 있던 응답이 끝나 컨테이너로 돌아온다, 안 끝나면 여기서 실패한다
        awaitEnded(opened);
        mockMvc.perform(asyncDispatch(opened)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("시나리오 6 : 구독 중 세션 만료 -> 다 내려간 응답이 인증 때문에 뒤집히지 않는다")
    void expiredSessionDoesNotOverturnSentResponse() throws Exception {
        // given : 붙어 있는 사이 세션 유효기간이 지난다
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);
        MvcResult opened = subscribe(liveAuctionId).andExpect(request().asyncStarted()).andReturn();
        fixClockAt(NOW.plusMinutes(SESSION_EXPIRED_MINUTES));

        // when : 서버가 끊어 요청이 두 번째로 디스패처를 통과한다, 인증도 이때 다시 돈다
        roomChannel.closeRoom(liveAuctionId);
        awaitEnded(opened);

        // then : 상태코드로는 알 수 없다, 응답이 이미 커밋돼 뒤늦은 401 이 200 을 덮지 못한다
        // 그래서 이 통과에서 인증 예외가 났는지를 본다, 나면 다 내려간 응답 뒤에 오류 본문이 따라붙는다
        MvcResult dispatched = mockMvc.perform(asyncDispatch(opened))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(dispatched.getResolvedException()).isNull();
    }

    // 끊는 순서가 옳은지는 여기서 볼 수 없다. 해제 콜백이 asyncDispatch 때만 돌아 MockMvc 로는
    // 순서를 관찰할 수 없고, 그것은 채널 단위테스트가 콜백을 흉내 내어 잡는다
    @Test
    @DisplayName("시나리오 7 : 여럿이 있는 방을 끊음 -> 모두의 연결이 끝나고 아무에게도 현황이 더 가지 않는다")
    void closingRoomEndsEveryConnection() throws Exception {
        // given : 같은 방에 둘이 붙어 있다, 두 번째가 들어오며 첫 번째는 갱신을 한 번 더 받았다
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);
        MvcResult first = subscribe(liveAuctionId).andExpect(request().asyncStarted()).andReturn();
        MvcResult second = subscribe(liveAuctionId).andExpect(request().asyncStarted()).andReturn();

        int sentToFirst = stateCount(first);
        int sentToSecond = stateCount(second);

        // when : 서버가 방을 끊는다, 한 명이 끝날 때마다 해제 콜백이 돌아온다
        roomChannel.closeRoom(liveAuctionId);
        awaitEnded(first);
        awaitEnded(second);
        mockMvc.perform(asyncDispatch(first));
        mockMvc.perform(asyncDispatch(second));

        // then : 둘 다 끝났고, 끝나는 사이에 현황이 더 실려 나가지도 않았다
        // 끝내기 전에 닫힘 표시가 먼저 서므로 그 틈에 방송이 와도 그 연결에는 써지지 않는다
        assertThat(stateCount(first)).isEqualTo(sentToFirst);
        assertThat(stateCount(second)).isEqualTo(sentToSecond);
    }

    @Test
    @DisplayName("시나리오 8 : 한 사람이 창을 둘 열어 구독 -> 접속자는 한 명으로 센다")
    void sameSessionTwiceCountsOneViewer() throws Exception {
        // given : 같은 사람이 창을 두 개 여는 것이라 쿠키가 같다
        long liveAuctionId = liveRoomWithTopBid("김민현", 12_500_000L);
        String sessionToken = loginAs(users.user("한구경", Role.DEALER));

        // when : 같은 세션으로 두 번 구독한다
        MvcResult first = subscribeAs(liveAuctionId, sessionToken)
                .andExpect(request().asyncStarted())
                .andReturn();
        subscribeAs(liveAuctionId, sessionToken).andExpect(status().isOk());

        // then : 연결은 둘인데 사람은 하나다, 두 번째 창이 열려도 접속자 수가 늘지 않는다
        assertThat(body(first))
                .contains("\"connectedCount\":1")
                .doesNotContain("\"connectedCount\":2");
    }

    // ================= 준비 ====================
    // 그 사람이 그 금액을 부른 진행 중인 방, 판매자와 시작 시각은 이 테스트가 보지 않는다
    // 한 사람이 두 번 넣는다, 건수와 사람 수가 달라야 두 값이 바꿔 실린 것을 단정이 잡는다
    private long liveRoomWithTopBid(String bidderName, long amount) {
        User bidder = users.user(bidderName, Role.DEALER);

        return rooms
                .room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .bid(NOW.minusMinutes(3), bidder, amount - 1_500_000L)
                .bid(NOW.minusMinutes(1), bidder, amount)
                .create();
    }

    // ================= 요청 ====================
    // 부를 때마다 다른 사람이 붙는다, UserSeeder 가 호출마다 새 회원을 만들기 때문이다
    private ResultActions subscribe(long auctionId) throws Exception {
        return subscribeAs(auctionId, loginAs(users.user("한구경", Role.DEALER)));
    }

    // 한 사람이 창을 여럿 여는 것을 흉내내려면 세션을 밖에서 정할 수 있어야 한다
    private ResultActions subscribeAs(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)));
    }

    // 브라우저의 EventSource 는 Accept: text/event-stream 만 보낸다, 거절 응답이 그 조건에서도
    // 협상에 걸리지 않고 나가는지가 여기서만 드러난다
    private ResultActions subscribeWithoutSession(long auctionId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId)
                .accept(MediaType.TEXT_EVENT_STREAM));
    }

    // 그 연결로 나간 현황의 개수, SSE 는 현황 하나가 data 한 줄이다
    private static int stateCount(MvcResult opened) {
        return body(opened).split("data:", -1).length - 1;
    }

    // 응답이 끝났는지 확인하는 대기다, 상한을 안 주면 spring-test 가 연결 만료 시간만큼 기다린다
    private static void awaitEnded(MvcResult opened) {
        opened.getAsyncResult(ASYNC_WAIT_MILLIS);
    }

    // 세션은 고정된 현재 시각 기준으로 발급된다, @BeforeEach 가 시각을 먼저 고정하므로 발급 직후 유효하다
    private String loginAs(User user) {
        return sessionService.issue(user);
    }

    // text/event-stream 에는 charset 이 안 붙어 getContentAsString() 이 ISO-8859-1 로 떨어진다
    // SSE 명세가 이 미디어타입을 항상 UTF-8 로 디코딩하게 정하므로 여기서도 그렇게 읽는다
    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
