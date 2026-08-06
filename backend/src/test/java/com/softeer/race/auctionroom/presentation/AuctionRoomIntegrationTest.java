package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매방 현황 상세 조회를 컨트롤러에서 DB까지
 * <p>
 * 1. 방 단계 판정
 * 개장·시작·마감 시각과 고정된 현재 시각으로 판정하고, 방을 열어 주지 않는 단계는 사유를 갈라 거절
 * <p>
 * 2. 접속자 집계 (인메모리)
 * 조회는 접속이 아니다, 열려 있는 구독이 없으면 0
 * <p>
 * 3. 입찰 집계와 최근 호가 (MySQL)
 * 건수와 사람 수, 최신순, 이름 마스킹, 조회자 본인 호가 판정
 * <p>
 * 4. 한 번의 조회로 읽어오는 차량과 낙찰자 (MySQL)
 * 경매·경매글·차량·낙찰자를 프로젝션 하나로 받음
 * <p>
 * 5. 직렬화
 * 마감 절대시각과 서버 시각을 오프셋 없는 KST 문자열로 내림
 * <p>
 * 6. 인증
 * 세션 쿠키 없이는 방을 열 수 없고, 본인 표시는 헤더가 아니라 쿠키 주인 기준이다
 */
@DisplayName("경매방 현황 조회 통합 테스트")
class AuctionRoomIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private SessionService sessionService;

    // 상수
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 8, 3, 20, 30);

    // 마감(시작 + 20분)에 결과 확인 5분까지 지난 시각이 되도록 뒤로 물린다
    private static final LocalDateTime CLOSED_START_AT = LocalDateTime.of(2026, 8, 3, 18, 30);

    // 개장(시작 30분 전)이 아직 오지 않도록 앞으로 민다
    private static final LocalDateTime NOT_OPEN_START_AT = LocalDateTime.of(2026, 8, 3, 22, 0);

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 진행 중 경매방 조회 -> 차량 요약 + 마스킹된 최근 호가에 내 입찰 표시 + 집계")
    void scenario1_LiveRoom_HappyPath() throws Exception {
        // given : 세 건을 두 사람이 넣은 진행 중인 방, 조회자는 그중 두 건을 넣은 김민현이다
        User viewer = users.user("김민현", Role.DEALER);

        long liveAuctionId = rooms
                .room(users.user("박판매", Role.GENERAL), START_AT)
                .mainPhotoUrl("https://cdn.race.dev/avante-1.jpg")
                .bid(LocalDateTime.of(2026, 8, 3, 20, 40, 5), viewer, 11_000_000L)
                .bid(LocalDateTime.of(2026, 8, 3, 20, 42, 18), users.user("남궁민수", Role.DEALER), 12_000_000L)
                .bid(LocalDateTime.of(2026, 8, 3, 20, 44, 31), viewer, 12_500_000L)
                .create();

        // when : 입찰자 한 명이 방을 조회
        ResultActions response = getRoom(liveAuctionId, loginAs(viewer));

        // then 1 : 상태가 아니라 시각으로 판정한 단계, 경매 식별자는 요청 경로가 아니라 조회 결과에서 온다
        response.andExpectAll(
                status().isOk(),
                jsonPath("$.auctionId").value(liveAuctionId),
                jsonPath("$.phase").value("LIVE"));

        // then 2 : 시작가와 현재가를 함께 내려 상승폭을 보인다
        response.andExpectAll(
                jsonPath("$.startPrice").value(10000000),
                jsonPath("$.currentPrice").value(12500000));

        // then 3 : 남은 초가 아니라 마감 절대시각과 서버 시각, 대기 화면을 위해 개장·시작 시각도 함께
        // 개장은 시작 30분 전, 마감은 시작 20분 뒤로 도메인이 정한다
        response.andExpectAll(
                jsonPath("$.openAt").value("2026-08-03T20:00:00"),
                jsonPath("$.startAt").value("2026-08-03T20:30:00"),
                jsonPath("$.endAt").value("2026-08-03T20:50:00"),
                jsonPath("$.serverTime").value("2026-08-03T20:45:12"));

        // then 4 : 목록을 거치지 않고 들어와도 화면을 채울 차량 정보가 함께 온다
        response.andExpectAll(
                jsonPath("$.vehicle.manufacturer").value("HYUNDAI"),
                jsonPath("$.vehicle.model").value("아반떼 CN7"),
                jsonPath("$.vehicle.modelYear").value(2022),
                jsonPath("$.vehicle.mileage").value(35000),
                jsonPath("$.vehicle.fuelType").value("GASOLINE"),
                jsonPath("$.vehicle.thumbnailUrl").value("https://cdn.race.dev/avante-1.jpg"));

        // then 5 : 차량을 특정할 수 있는 번호판은 응답에 없다
        response.andExpect(jsonPath("$.vehicle.plateNumber").doesNotExist());

        // then 5-1 : 차량 정보는 한 덩어리로 온다, 사진이 최상위에 남으면 화면이 두 군데서 조립한다
        response.andExpect(jsonPath("$.thumbnailUrl").doesNotHaveJsonPath());

        // then 6 : 세 건을 두 사람이 넣었으므로 건수는 3이고 사람 수는 2다
        response.andExpectAll(
                jsonPath("$.bidCount").value(3),
                jsonPath("$.bidderCount").value(2));

        // then 7 : 조회는 접속이 아니다, 스트림에 열려 있는 구독이 없으므로 0이다
        response.andExpect(jsonPath("$.connectedCount").value(0));

        // then 8 : 최신순, 원본이 아니라 마스킹된 이름이 실린다, 마스킹 규칙 자체는 단위테스트가 본다
        response.andExpectAll(
                jsonPath("$.recentBids.length()").value(3),
                jsonPath("$.recentBids[0].name").value("김*현"),
                jsonPath("$.recentBids[0].amount").value(12500000),
                jsonPath("$.recentBids[0].bidAt").value("2026-08-03T20:44:31"));

        // then 9 : 누가 넣었는지는 내려보내지 않는다, 식별자가 나가면 마스킹이 무의미해진다
        response.andExpect(jsonPath("$.recentBids[0].bidderId").doesNotExist());

        // then 10 : 조회자가 넣은 호가만 내 입찰로 표시된다, 마스킹된 이름으로는 구분할 수 없다
        response.andExpectAll(
                jsonPath("$.recentBids[0].mine").value(true),
                jsonPath("$.recentBids[1].mine").value(false),
                jsonPath("$.recentBids[2].mine").value(true));

        // then 11 : 역할은 값으로 내리고 배지 문구는 화면이 정한다
        response.andExpect(jsonPath("$.recentBids[0].role").value("DEALER"));

        // then 12 : 낙찰 확정 전이라 낙찰자가 없다
        response.andExpect(jsonPath("$.winner").isEmpty());
    }

    @Test
    @DisplayName("시나리오 2 : 단계가 어긋난 방 조회 -> 아직 열리지 않음과 이미 끝남을 code 로 가른다")
    void scenario2_PhaseMismatch_Rejected() throws Exception {
        // given : 마감 후 5분까지 지난 방과, 개장 시각이 아직 오지 않은 방
        User viewer = users.user("한구경", Role.DEALER);

        long closedAuctionId = rooms.room(users.user("최판매", Role.GENERAL), CLOSED_START_AT).create();
        long notOpenAuctionId = rooms.room(users.user("정판매", Role.GENERAL), NOT_OPEN_START_AT).create();

        // when & then 1 : 실시간 화면의 수명이 끝났다, 화면은 이 사유를 보고 결과로 옮겨간다
        getRoom(closedAuctionId, loginAs(viewer)).andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("ROOM_ALREADY_CLOSED"));

        // when & then 2 : 기다렸다 다시 오면 되는 방이라 사유가 다르다, 화면은 개장 안내로 옮겨간다
        getRoom(notOpenAuctionId, loginAs(viewer)).andExpectAll(
                status().isConflict(),
                jsonPath("$.code").value("ROOM_NOT_OPEN_YET"));
    }

    @Test
    @DisplayName("시나리오 3 : 삭제된 경매글의 방 조회 -> 진행 중이어도 없는 방으로 취급한다")
    void scenario3_DeletedPost_NotFound() throws Exception {
        // given : 진행 중으로 둔다, 삭제 조건이 빠지면 200 LIVE 가 나가 이 단정이 깨지도록
        User viewer = users.user("한구경", Role.DEALER);
        long deletedPostAuctionId = rooms.room(users.user("정판매", Role.GENERAL), START_AT).create();
        deletePost(deletedPostAuctionId);

        // when : 글이 내려간 경매의 방을 조회
        ResultActions response = getRoom(deletedPostAuctionId, loginAs(viewer));

        // then : 도달할 수 없는 자원이므로 단계를 알리지 않고 404 다
        response.andExpectAll(
                status().isNotFound(),
                jsonPath("$.code").value("AUCTION_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 4 : 세션 없이 방 조회 -> 방의 존재 여부를 알리지 않고 401")
    void scenario4_WithoutSession_Unauthorized() throws Exception {
        // given : 조회하면 200 이 나올 정상적인 진행 중 방이다, 401 이 방 상태 때문이 아님을 분명히 한다
        long liveAuctionId = rooms.room(users.user("박판매", Role.GENERAL), START_AT).create();

        // when & then : 쿠키가 없으면 인터셉터에서 끊긴다
        getRoomWithoutSession(liveAuctionId).andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("시나리오 5 : 입찰이 없는 경매 -> 방과 목록이 같은 현재가를 보인다")
    void scenario5_NoBid_SamePriceInRoomAndList() throws Exception {
        // given : 입찰이 한 건도 없는 진행 중인 방
        User viewer = users.user("한구경", Role.DEALER);
        long auctionId = rooms.room(users.user("박판매", Role.GENERAL), START_AT).create();

        // when : 같은 경매를 방과 목록에서 각각 본다
        ResultActions room = getRoom(auctionId, loginAs(viewer));
        ResultActions list = mockMvc.perform(get("/api/auctions"));

        // then : 입찰이 없으면 현재가는 시작가다, 두 화면이 각자 해소하므로 같은 값이어야 한다
        // 단계도 같은 판정을 써야 한다, 갈라지면 같은 경매가 목록과 방에서 다른 배지로 보인다
        room.andExpectAll(
                status().isOk(),
                jsonPath("$.phase").value("LIVE"),
                jsonPath("$.currentPrice").value(10000000));

        list.andExpectAll(
                status().isOk(),
                jsonPath("$.content[0].auctionId").value(auctionId),
                jsonPath("$.content[0].phase").value("LIVE"),
                jsonPath("$.content[0].currentPrice").value(10000000));
    }

    // ================= 도메인에 아직 없는 전이 ====================
    // 게시글 삭제는 도메인에 메서드가 없어 여기서만 직접 쓴다

    private void deletePost(long auctionId) {
        jdbcTemplate.update("""
                update auction_post p join auction a on a.post_id = p.id
                set p.deleted_at = ?
                where a.id = ?
                """, NOW.minusMinutes(5), auctionId);
    }

    // ================= 요청 ====================
    // 세션은 고정된 현재 시각 기준으로 발급된다, @BeforeEach 가 시각을 먼저 고정하므로 발급 직후 유효하다
    private String loginAs(User user) {
        return sessionService.issue(user);
    }

    private ResultActions getRoom(long auctionId, String sessionToken) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, sessionToken)));
    }

    private ResultActions getRoomWithoutSession(long auctionId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId));
    }
}
