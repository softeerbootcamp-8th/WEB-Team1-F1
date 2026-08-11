package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목록 카드와 경매방이 같은 시청자 수를 보이는지, 컨트롤러에서 DB까지
 * <p>
 * 단위테스트로는 볼 수 없다. 이 규칙의 전부가 두 화면이 같은 채널 하나를 나눠 쓴다는 것인데,
 * 채널을 목으로 끼우면 그 공유가 사라져 무엇을 넣든 통과한다. 구독도 SSE 컨트롤러가 실제로
 * 열어야 세어지므로 서비스만 불러서는 만들 수 없다.
 * <p>
 * 규칙은 {@code AuctionListService.toCard} 주석에만 있고 아무도 실행하지 않는다. 목록은 단계를 보고
 * 0을 내리는 갈래를 따로 갖고 있어 어긋날 자리가 실제로 있다.
 * <p>
 * 창을 둘 여는 것이 이 테스트의 핵심이다. 하나만 열면 구독을 세든 사람을 세든 답이 1로 같아
 * 두 화면이 서로 다른 단위를 쓰고 있어도 통과한다.
 */
@DisplayName("목록·경매방 시청자 수 일치 통합 테스트")
class ViewerCountParityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 시작이 15분 전이면 진행 중이다, 마감(시작 + 20분)까지 5분 남아 연장 창에도 걸리지 않는다
    private static final LocalDateTime LIVE_START_AT = NOW.minusMinutes(15);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("한 사람이 창을 둘 열면 -> 목록과 경매방이 모두 한 명으로 센다")
    void listAndRoomAgreeOnViewerCount() throws Exception {
        // given : 진행 중인 방 하나에 한 사람이 같은 쿠키로 창을 둘 연다
        String session = sessionService.issue(users.user("한구경", Role.DEALER));
        long auctionId = liveRoom();

        subscribe(auctionId, session);
        subscribe(auctionId, session);

        // when & then 1 : 경매방은 연결이 둘이어도 사람 하나로 센다
        // 조회는 접속이 아니라서 이 요청 자체는 수를 늘리지 않는다
        mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                        .cookie(sessionCookie(session)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.connectedCount").value(1));

        // then 2 : 목록도 같은 수를 보인다, 어긋나면 같은 경매가 두 화면에서 다르게 보인다
        // 방이 하나뿐임을 먼저 못 박는다, 안 그러면 아래 카드가 그 방인지 알 수 없다
        mockMvc.perform(get("/api/auctions"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content.length()").value(1),
                        jsonPath("$.content[0].auctionId").value((int) auctionId),
                        jsonPath("$.content[0].connectedCount").value(1));
    }

    @Test
    @DisplayName("마감이 지났는데 구독이 아직 남아 있으면 -> 조회도 목록도 접속자로 세지 않는다")
    void leftoverSubscriptionIsNotCountedAfterDeadline() throws Exception {
        // given : 진행 중인 방을 한 사람이 보고 있다
        String session = sessionService.issue(users.user("한구경", Role.DEALER));
        long auctionId = liveRoom();

        subscribe(auctionId, session);

        // when : 마감 시각이 지난다, 확정도 정리도 아직 돌지 않아 구독은 그대로 남아 있다
        fixClockAt(LIVE_START_AT.plusMinutes(21));

        // then : 연결을 열어 두지 않는 단계의 구독은 접속자가 아니다, 곧 끊길 연결을 사람으로 세지 않는다
        mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                        .cookie(sessionCookie(session)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.phase").value("RESULT"),
                        jsonPath("$.connectedCount").value(0));

        mockMvc.perform(get("/api/auctions"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content[0].connectedCount").value(0));
    }

    // ================= 준비 ====================
    // 입찰은 넣지 않는다, 이 테스트가 보는 것은 사람 수뿐이고 현재가는 시작가로 채워진다
    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), LIVE_START_AT).create();
    }

    // ================= 요청 ====================
    // 같은 쿠키로 두 번 연다, 창 둘이 한 사람이라는 것이 이 테스트가 세우는 상황이다
    private void subscribe(long auctionId, String sessionToken) throws Exception {
        mockMvc.perform(get("/api/auctions/{auctionId}/room/stream", auctionId)
                        .cookie(sessionCookie(sessionToken)))
                .andExpectAll(status().isOk(), request().asyncStarted());
    }

    private static Cookie sessionCookie(String sessionToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken);
    }
}
