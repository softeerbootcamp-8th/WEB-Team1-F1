package com.softeer.race.auctionroom.presentation;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개장 전 경매방 안내를 컨트롤러에서 DB까지
 * <p>
 * 화면이 그릴 것은 "언제부터 들어갈 수 있는지" 하나다. 남은 초가 아니라 입장 가능 시각과
 * 서버 시각을 내려 클라이언트가 직접 센다. 이미 열린 방에는 안내가 없고 방 조회로 가야 한다.
 */
@DisplayName("개장 전 경매방 안내 통합 테스트")
class RoomOpeningIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 개장은 시작 30분 전이라 21:30 에 열린다, 지금은 그 전이다
    private static final LocalDateTime NOT_OPEN_START_AT = LocalDateTime.of(2026, 8, 3, 22, 0);

    // 이미 열려 진행 중인 방
    private static final LocalDateTime LIVE_START_AT = LocalDateTime.of(2026, 8, 3, 20, 30);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 개장 전 방 안내 -> 차량과 시작가에 입장 가능 시각, 실시간 값은 없다")
    void scenario1_BeforeOpen_HappyPath() throws Exception {
        // given : 개장 시각이 아직 오지 않은 방
        User viewer = users.user("한구경", Role.DEALER);

        long notOpenAuctionId = rooms.room(users.user("박판매", Role.GENERAL), NOT_OPEN_START_AT)
                .model("더 뉴 셀토스")
                .thumbnailUrl("https://cdn.race.dev/seltos-1.jpg")
                .startPrice(18_000_000L)
                .create();

        // when : 목록에서 방을 눌러 들어온다
        ResultActions response = getOpening(notOpenAuctionId, loginAs(viewer));

        // then 1 : 개장은 시작 30분 전이라는 도메인 규칙이 시각으로 드러난다
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.auctionId").value(notOpenAuctionId),
                jsonPath("$.openAt").value("2026-08-03T21:30:00"),
                jsonPath("$.startAt").value("2026-08-03T22:00:00"));

        // then 2 : 남은 시간은 서버가 세지 않는다, 두 시각의 차이로 화면이 센다
        response.andExpect(jsonPath("$.serverTime").value("2026-08-03T20:45:12"));

        // then 3 : 목록을 거치지 않고 들어와도 어떤 차를 기다리는지 보인다
        response.andExpectAll(
                jsonPath("$.vehicle.manufacturer").value("HYUNDAI"),
                jsonPath("$.vehicle.model").value("더 뉴 셀토스"),
                jsonPath("$.vehicle.thumbnailUrl").value("https://cdn.race.dev/seltos-1.jpg"),
                jsonPath("$.startPrice").value(18000000));

        // then 4 : 아직 아무 일도 일어나지 않은 방이라 실시간 값이 나갈 자리가 없다
        // doesNotExist 는 값이 null 이어도 통과하므로, 스키마에 아예 없다는 것은 이쪽으로 단정한다
        response.andExpectAll(
                jsonPath("$.connectedCount").doesNotHaveJsonPath(),
                jsonPath("$.currentPrice").doesNotHaveJsonPath(),
                jsonPath("$.recentBids").doesNotHaveJsonPath(),
                jsonPath("$.phase").doesNotHaveJsonPath());

        // then 5 : 차량 정보는 한 덩어리로 온다, 사진이 최상위에 남으면 화면이 두 군데서 조립한다
        response.andExpect(jsonPath("$.thumbnailUrl").doesNotHaveJsonPath());
    }

    @Test
    @DisplayName("시나리오 2 : 이미 열린 방의 안내 -> 안내할 것이 없으므로 거절한다")
    void scenario2_AlreadyOpen_Rejected() throws Exception {
        // given : 진행 중인 방
        User viewer = users.user("한구경", Role.DEALER);
        long liveAuctionId = rooms.room(users.user("박판매", Role.GENERAL), LIVE_START_AT).create();

        // when : 개장 안내를 요청한다
        ResultActions response = getOpening(liveAuctionId, loginAs(viewer));

        // then : 자원이 없는 게 아니라 단계가 지나간 것이다, 화면은 방 조회로 옮겨간다
        response.andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("ROOM_ALREADY_OPEN"));
    }

    private String loginAs(User user) {
        return sessionService.issue(user);
    }

    private ResultActions getOpening(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/opening", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)));
    }
}
