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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 끝난 경매의 결과 요약을 컨트롤러에서 DB까지
 * <p>
 * 1. 결과 판정
 * 시각이 아니라 스케줄러가 확정한 상태를 본다, 확정 전에는 결과가 없다
 * <p>
 * 2. 유찰 구분
 * 아무도 입찰하지 않은 경매는 낙찰가도 낙찰자도 없이 유찰로 나간다
 * <p>
 * 3. 개인화
 * 셋 중 낙찰자 본인 여부만 조회자마다 다르고 나머지는 같다
 * <p>
 * 4. 실시간 값의 부재
 * 더 이상 바뀌지 않는 경매라 접속자 수도 서버 시각도 나가지 않는다
 */
@DisplayName("경매 결과 요약 조회 통합 테스트")
class RoomResultIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 마감(시작 + 20분)이 지나 종료 확정까지 끝난 경매
    private static final LocalDateTime ENDED_START_AT = LocalDateTime.of(2026, 8, 3, 18, 30);

    // 아직 시작하지 않은 경매
    private static final LocalDateTime NOT_ENDED_START_AT = LocalDateTime.of(2026, 8, 3, 22, 0);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 낙찰로 끝난 경매 -> 차량과 시작가에 낙찰가·낙찰자·입찰 건수, 본인 여부만 조회자마다 다르다")
    void scenario1_Sold_HappyPath() throws Exception {
        // given : 두 사람이 세 번 넣었고 마지막에 넣은 이준호가 낙찰자다
        User winner = users.user("이준호", Role.DEALER);
        User loser = users.user("남궁민수", Role.DEALER);

        long endedAuctionId = rooms.room(users.user("최판매", Role.GENERAL), ENDED_START_AT)
                .model("더 뉴 셀토스")
                .thumbnailUrl("https://cdn.race.dev/seltos-1.jpg")
                .startPrice(20_000_000L)
                .bid(ENDED_START_AT.plusMinutes(5), loser, 21_000_000L)
                .bid(ENDED_START_AT.plusMinutes(10), winner, 22_000_000L)
                .bid(ENDED_START_AT.plusMinutes(15), loser, 23_000_000L)
                .bid(ENDED_START_AT.plusMinutes(18), winner, 24_000_000L)
                .closed()
                .create();

        // when : 낙찰자 본인이 결과를 연다
        ResultActions response = getResult(endedAuctionId, loginAs(winner));

        // then 1 : 유찰과 낙찰을 유추가 아니라 값으로 가른다
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.auctionId").value(endedAuctionId),
                jsonPath("$.outcome").value("SOLD"));

        // then 2 : 시작가와 최종 낙찰가를 함께 내려 얼마나 올랐는지 보인다
        response.andExpectAll(
                jsonPath("$.startPrice").value(20000000),
                jsonPath("$.winningPrice").value(24000000));

        // then 3 : 목록을 거치지 않고 들어와도 어떤 차였는지 보인다
        response.andExpectAll(
                jsonPath("$.vehicle.model").value("더 뉴 셀토스"),
                jsonPath("$.thumbnailUrl").value("https://cdn.race.dev/seltos-1.jpg"));

        // then 4 : 네 건이 들어왔다, 최근 호가 목록과 달리 전체를 센다
        response.andExpect(jsonPath("$.bidCount").value(4));

        // then 5 : 낙찰자는 호가와 같은 규칙으로 마스킹되고, 본인 여부는 이름 비교 없이 내려간다
        response.andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(true));

        // then 6 : 더 이상 바뀌지 않는 경매라 실시간 값이 나갈 자리가 없다
        // doesNotExist 는 값이 null 이어도 통과하므로, 스키마에 아예 없다는 것은 이쪽으로 단정한다
        response.andExpectAll(
                jsonPath("$.connectedCount").doesNotHaveJsonPath(),
                jsonPath("$.serverTime").doesNotHaveJsonPath(),
                jsonPath("$.recentBids").doesNotHaveJsonPath());

        // then 7 : 탈락한 사람이 같은 결과를 보면 이름은 같고 본인 표시만 꺼진다
        getResult(endedAuctionId, loginAs(loser)).andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.winningPrice").value(24000000));
    }

    @Test
    @DisplayName("시나리오 2 : 아무도 입찰하지 않은 경매 -> 유찰이고 낙찰가도 낙찰자도 없다")
    void scenario2_Unsold() throws Exception {
        // given : 입찰 없이 마감돼 유찰로 확정된 경매
        User viewer = users.user("한구경", Role.DEALER);

        long unsoldAuctionId = rooms.room(users.user("정판매", Role.GENERAL), ENDED_START_AT)
                .startPrice(30_000_000L)
                .closed()
                .create();

        // when : 결과를 연다
        ResultActions response = getResult(unsoldAuctionId, loginAs(viewer));

        // then : 낙찰자가 비어 있다는 것으로 유추하게 두지 않는다, 유찰이라고 말한다
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.outcome").value("UNSOLD"),
                jsonPath("$.startPrice").value(30000000),
                jsonPath("$.bidCount").value(0));

        // 키를 빼지 않고 null 로 내린다, 소비자가 필드 유무가 아니라 값으로 분기하게 한다
        response.andExpectAll(
                jsonPath("$.winningPrice").value(nullValue()),
                jsonPath("$.winner").value(nullValue()));
    }

    @Test
    @DisplayName("시나리오 3 : 아직 끝나지 않은 경매의 결과 -> 확정 전이라 결과가 없다")
    void scenario3_NotEnded_Rejected() throws Exception {
        // given : 마감이 오지 않아 낙찰자가 확정되지 않은 경매
        User viewer = users.user("한구경", Role.DEALER);
        long runningAuctionId = rooms.room(users.user("박판매", Role.GENERAL), NOT_ENDED_START_AT).create();

        // when : 결과를 미리 열어 본다
        ResultActions response = getResult(runningAuctionId, loginAs(viewer));

        // then : 자원이 없는 게 아니라 아직 만들어지지 않았다, 유찰이라고 답하면 거짓말이 된다
        response.andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("AUCTION_NOT_ENDED"));
    }

    private String loginAs(User user) {
        return sessionService.issue(user);
    }

    private ResultActions getResult(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/result", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)));
    }
}
