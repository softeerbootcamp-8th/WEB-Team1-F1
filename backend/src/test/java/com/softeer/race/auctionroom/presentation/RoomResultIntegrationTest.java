package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.VehicleKeyword;
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
 * 낙찰자·탈락자·판매자·구경꾼 넷이 응답만 보고 갈린다, 나머지 값은 누가 봐도 같다
 * <p>
 * 4. 실시간 값의 부재
 * 더 이상 바뀌지 않는 경매라 접속자 수도 호가창도 나가지 않는다
 * 서버 시각은 결과값이 아니라 남은 열람 시간을 세는 기준이라 예외로 나간다
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
                .photos("https://cdn.race.dev/seltos-1.jpg", "https://cdn.race.dev/seltos-2.jpg")
                .diagnosticReportUrl("https://cdn.race.dev/seltos-report.pdf")
                .keywords(VehicleKeyword.GOOD_TIRE, VehicleKeyword.UNDERBODY_INTACT)
                .startPrice(20_000_000L)
                .bid(ENDED_START_AT.plusMinutes(5), loser, 21_000_000L)
                .bid(ENDED_START_AT.plusMinutes(10), winner, 22_000_000L)
                .bid(ENDED_START_AT.plusMinutes(15), loser, 23_000_000L)
                // 마감 20초 전 입찰이라 소프트클로즈가 걸린다, 연장 횟수가 0 이면 세는지 알 수 없다
                .bid(ENDED_START_AT.plusMinutes(19).plusSeconds(40), winner, 24_000_000L)
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
                jsonPath("$.vehicle.imageUrls.length()").value(2),
                jsonPath("$.vehicle.imageUrls[0]").value("https://cdn.race.dev/seltos-1.jpg"),
                jsonPath("$.vehicle.diagnosticReportUrl")
                        .value("https://cdn.race.dev/seltos-report.pdf"),
                jsonPath("$.vehicle.keywords.length()").value(2),
                jsonPath("$.vehicle.keywords[0]").value("UNDERBODY_INTACT"),
                jsonPath("$.vehicle.keywords[1]").value("GOOD_TIRE"));

        // then 3-1 : 차량 정보는 한 덩어리로 온다, 사진이 최상위에 남으면 화면이 두 군데서 조립한다
        response.andExpect(jsonPath("$.thumbnailUrl").doesNotHaveJsonPath());

        // then 4 : 네 건이 들어왔다, 최근 호가 목록과 달리 전체를 센다
        // 두 사람이 넣었으므로 건수와 사람 수가 다르다, 같은 값이면 어느 쪽을 세는지 알 수 없다
        response.andExpectAll(
                jsonPath("$.bidCount").value(4),
                jsonPath("$.bidderCount").value(2));

        // then 4-1 : 마감 임박 입찰이 마감을 20초 뒤로 밀었다, 그 사실이 횟수와 시각 둘 다에 남는다
        response.andExpectAll(
                jsonPath("$.extensionCount").value(1),
                jsonPath("$.startAt").value("2026-08-03T18:30:00"),
                jsonPath("$.endAt").value("2026-08-03T18:50:10"));

        // then 4-2 : 어느 입찰이 마감을 밀었는지는 곡선의 점에 남는다, 횟수만으로는 그 자리를 못 그린다
        response.andExpectAll(
                jsonPath("$.priceCurve[2].extended").value(false),
                jsonPath("$.priceCurve[3].extended").value(true));

        // then 5 : 낙찰자는 호가와 같은 규칙으로 마스킹되고, 본인 여부는 이름 비교 없이 내려간다
        response.andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(true));

        // then 6 : 결과는 더 이상 바뀌지 않으므로 실시간 값이 나갈 자리가 없다
        // doesNotExist 는 값이 null 이어도 통과하므로, 스키마에 아예 없다는 것은 이쪽으로 단정한다
        response.andExpectAll(
                jsonPath("$.viewerCount").doesNotHaveJsonPath(),
                jsonPath("$.recentBids").doesNotHaveJsonPath());

        // then 6-1 : 서버 시각은 결과값이 아니라 남은 열람 시간을 세는 기준이라 종료 시각과 짝으로 나간다
        // 종료 시각은 연장된 마감에서 다시 계산된다, 원래 마감에 더하면 20초 어긋난다
        response.andExpectAll(
                jsonPath("$.resultEndAt").value("2026-08-03T18:55:10"),
                jsonPath("$.serverTime").value("2026-08-03T20:45:12"));

        // then 7 : 탈락한 사람이 같은 결과를 보면 이름은 같고 본인 표시만 꺼진다
        getResult(endedAuctionId, loginAs(loser)).andExpectAll(
                jsonPath("$.winner.name").value("이*호"),
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.winningPrice").value(24000000));
    }

    // 낙찰자, 탈락자, 판매자, 구경꾼이 같은 경매를 서로 다르게 읽는다
    // 화면의 첫 문장이 이 넷으로 갈리므로 응답만 보고 갈릴 수 있어야 한다
    @Test
    @DisplayName("시나리오 1-1 : 같은 결과를 네 사람이 보면 -> 자기 자리만 다르게 온다")
    void scenario1_1_StandingDiffersByViewer() throws Exception {
        // given : 두 사람이 넣었고 남궁민수가 더 높이 부른 이준호에게 밀렸다
        User seller = users.user("최판매", Role.GENERAL);
        User winner = users.user("이준호", Role.DEALER);
        User loser = users.user("남궁민수", Role.DEALER);
        User onlooker = users.user("한구경", Role.DEALER);

        long endedAuctionId = rooms.room(seller, ENDED_START_AT)
                .startPrice(20_000_000L)
                .bid(ENDED_START_AT.plusMinutes(5), loser, 21_000_000L)
                .bid(ENDED_START_AT.plusMinutes(10), winner, 22_000_000L)
                .bid(ENDED_START_AT.plusMinutes(15), loser, 23_000_000L)
                .bid(ENDED_START_AT.plusMinutes(18), winner, 24_000_000L)
                .closed()
                .create();

        // then 1 : 낙찰자는 자기가 가장 높았으므로 1등이고, 자기가 넣은 것 중 최고가가 온다
        getResult(endedAuctionId, loginAs(winner)).andExpectAll(
                jsonPath("$.winner.mine").value(true),
                jsonPath("$.sellerIsMine").value(false),
                jsonPath("$.myStanding.highestAmount").value(24000000),
                jsonPath("$.myStanding.rank").value(1));

        // then 2 : 탈락자는 두 번 넣었으므로 높은 쪽이 오고, 위에 한 사람이 있어 2등이다
        // 입찰자 수와 짝지어 "2명 중 2번째" 가 된다
        getResult(endedAuctionId, loginAs(loser)).andExpectAll(
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.myStanding.highestAmount").value(23000000),
                jsonPath("$.myStanding.rank").value(2),
                jsonPath("$.bidderCount").value(2));

        // then 3 : 판매자는 입찰한 적이 없어 성적이 없고, 대신 파는 사람으로 판정된다
        getResult(endedAuctionId, loginAs(seller)).andExpectAll(
                jsonPath("$.sellerIsMine").value(true),
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.myStanding").value(nullValue()));

        // then 4 : 구경꾼은 어느 쪽도 아니다, 성적 자리가 필드마다 null 이 아니라 통째로 비어 있다
        getResult(endedAuctionId, loginAs(onlooker)).andExpectAll(
                jsonPath("$.sellerIsMine").value(false),
                jsonPath("$.winner.mine").value(false),
                jsonPath("$.myStanding").value(nullValue()));
    }

    // 화면은 시작가에서 낙찰가까지 오른 과정을 선으로 그리고 내 입찰만 다른 색으로 찍는다
    @Test
    @DisplayName("시나리오 1-2 : 가격이 오른 과정 -> 넣은 순서대로 오고 내 입찰만 표시된다")
    void scenario1_2_PriceCurve() throws Exception {
        // given : 두 사람이 번갈아 네 번 넣어 값이 올랐다
        User winner = users.user("이준호", Role.DEALER);
        User loser = users.user("남궁민수", Role.DEALER);

        long endedAuctionId = rooms.room(users.user("최판매", Role.GENERAL), ENDED_START_AT)
                .startPrice(20_000_000L)
                .bid(ENDED_START_AT.plusMinutes(5), loser, 21_000_000L)
                .bid(ENDED_START_AT.plusMinutes(10), winner, 22_000_000L)
                .bid(ENDED_START_AT.plusMinutes(15), loser, 23_000_000L)
                .bid(ENDED_START_AT.plusMinutes(18), winner, 24_000_000L)
                .closed()
                .create();

        ResultActions response = getResult(endedAuctionId, loginAs(loser));

        // then 1 : 호가창의 스무 건 제한과 달리 전부 오고, 최신순이 아니라 시간순이라 선이 왼쪽부터 그려진다
        response.andExpectAll(
                jsonPath("$.priceCurve.length()").value(4),
                jsonPath("$.priceCurve[0].amount").value(21000000),
                jsonPath("$.priceCurve[0].at").value("2026-08-03T18:35:00"));

        // then 2 : 마지막 점이 곧 낙찰가다, 어긋나면 곡선의 끝과 요약 숫자가 다르게 보인다
        response.andExpect(jsonPath("$.priceCurve[3].amount").value(24000000))
                .andExpect(jsonPath("$.winningPrice").value(24000000));

        // then 3 : 내가 넣은 두 점만 표시된다, 보는 사람이 바뀌면 표시도 옮겨간다
        response.andExpectAll(
                jsonPath("$.priceCurve[0].mine").value(true),
                jsonPath("$.priceCurve[1].mine").value(false),
                jsonPath("$.priceCurve[2].mine").value(true),
                jsonPath("$.priceCurve[3].mine").value(false));

        // then 4 : 곡선은 이름을 싣지 않는다, 누가 얼마를 불렀는지는 호가창이 답할 일이다
        response.andExpectAll(
                jsonPath("$.priceCurve[0].name").doesNotHaveJsonPath(),
                jsonPath("$.priceCurve[0].bidderId").doesNotHaveJsonPath());

        // then 5 : 마감 임박에 들어온 입찰이 없어 마감을 밀어낸 점도 없다
        response.andExpectAll(
                jsonPath("$.extensionCount").value(0),
                jsonPath("$.priceCurve[3].extended").value(false));
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

        // 오른 적이 없으니 곡선은 빈 배열이다, 여기만 null 로 내리면 화면이 배열 하나를 두 가지로 다뤄야 한다
        response.andExpectAll(
                jsonPath("$.priceCurve").isArray(),
                jsonPath("$.priceCurve.length()").value(0),
                jsonPath("$.bidderCount").value(0));

        // 키워드가 없는 차량도 null 이 아니라 빈 배열로 내린다
        response.andExpectAll(
                jsonPath("$.vehicle.keywords").isArray(),
                jsonPath("$.vehicle.keywords.length()").value(0));
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
                jsonPath("$.code").value("ROOM_RESULT_NOT_READY"));
    }

    private String loginAs(User user) {
        return sessionService.issue(user);
    }

    private ResultActions getResult(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room/result", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)));
    }
}
