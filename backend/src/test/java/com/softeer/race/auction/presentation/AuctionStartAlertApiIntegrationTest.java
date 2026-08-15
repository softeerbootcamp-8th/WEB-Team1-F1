package com.softeer.race.auction.presentation;

import com.softeer.race.auction.application.AuctionStartAlertService;
import com.softeer.race.auction.application.AuctionStarter;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.TestClock;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시작 알림을 신청하고 그 상태를 다시 읽는 길
 * <p>
 * <b>경매는 시더로 세운다.</b> 신청 가능 여부를 경매 상태와 시작 시각으로 판정하므로, 상태를 SQL 로
 * 심으면 도메인이 만들 수 없는 조합까지 만들어 두고 통과시킬 수 있다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 신청 한 건이 한 트랜잭션이라는 전제와, 잠금이 두 요청의
 * 순서를 정한다는 검증이 테스트 트랜잭션 안에서는 관측되지 않는다. 정리는 부모의 {@code @AfterEach} 가 맡는다.
 * <p>
 * <b>로그인 대신 세션을 직접 심는다.</b> 이 테스트가 볼 것은 인증이 아니라 신청 규칙이다.
 */
@DisplayName("경매 시작 알림 신청·조회 통합 테스트")
class AuctionStartAlertApiIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);

    /** 신청이 가능한 예정 경매, 방은 이미 열려 있다(시작 30분 전 개장) */
    private static final LocalDateTime STARTS_SOON = NOW.plusMinutes(10);

    /** 기준 시각에는 이미 시작해 있는 경매 */
    private static final LocalDateTime STARTED_ALREADY = NOW.minusMinutes(5);

    private static final long ABSENT_AUCTION_ID = 999_999L;

    @Autowired
    private AuctionStartAlertService auctionStartAlertService;

    @Autowired
    private AuctionStarter auctionStarter;

    private User seller;
    private User alice;
    private User bob;

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        seller = users.user("박판매", Role.GENERAL);
        alice = users.user("김앨리스", Role.DEALER);
        bob = users.user("이밥", Role.DEALER);
    }

    @Test
    @DisplayName("시나리오 1 : 예정 경매에 처음 신청하면 201이고 신청이 한 건 생긴다")
    void scenario1_FirstSubscribeIsCreated() throws Exception {
        long auctionId = scheduledAuction();

        subscribe(auctionId, alice)
                .andExpect(status().isCreated());

        assertThat(countSubscriptions()).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 같은 회원이 다시 신청하면 204이고 신청은 여전히 한 건이다")
    void scenario2_RepeatedSubscribeIsIdempotent() throws Exception {
        long auctionId = scheduledAuction();

        subscribe(auctionId, alice).andExpect(status().isCreated());

        // 취소가 없어 도달할 상태가 하나뿐이다, 두 번째 요청이 실패할 이유가 없다
        subscribe(auctionId, alice).andExpect(status().isNoContent());
        subscribe(auctionId, alice).andExpect(status().isNoContent());

        assertThat(countSubscriptions()).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 3 : 이미 시작된 경매에는 신청할 수 없다")
    void scenario3_RejectsStartedAuction() throws Exception {
        long auctionId = rooms.room(seller, STARTED_ALREADY).create();
        TestClock.INSTANCE.runAt(STARTED_ALREADY, () -> auctionStarter.start(auctionId));

        // 잠금을 얻은 뒤 상태를 다시 보지 않으면 여기가 통과한다
        subscribe(auctionId, alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("START_ALERT_NOT_OPEN"));

        assertThat(countSubscriptions()).isZero();
    }

    @Test
    @DisplayName("시나리오 4 : 시작 시각 정각에는 신청할 수 없다")
    void scenario4_RejectsAtExactStartTime() throws Exception {
        // given : 상태는 아직 예약이지만 기준 시각이 시작 시각과 같다
        long auctionId = rooms.room(seller, NOW).create();

        // 정각을 신청 가능으로 두면 "신청 가능"과 "시작 전이 대상"이 같은 시각에 동시에 참이 된다
        subscribe(auctionId, alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("START_ALERT_NOT_OPEN"));
    }

    @Test
    @DisplayName("시나리오 5 : 이미 끝난 경매에는 신청할 수 없다")
    void scenario5_RejectsEndedAuction() throws Exception {
        // given : 입찰 없이 마감까지 지나 유찰로 끝났다
        long auctionId = rooms.room(seller, NOW.minusHours(1)).closed().create();

        // 시작 판정을 !isStartableAt 으로 뒤집어 쓰면 끝난 경매가 신청 가능이 된다
        subscribe(auctionId, alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("START_ALERT_NOT_OPEN"));
    }

    @Test
    @DisplayName("시나리오 6 : 서로 다른 회원은 같은 경매에 각자 신청이 남는다")
    void scenario6_UniquenessIsPerUser() throws Exception {
        long auctionId = scheduledAuction();

        subscribe(auctionId, alice).andExpect(status().isCreated());

        // 유일 제약을 경매 단위로 잡았다면 여기가 막힌다
        subscribe(auctionId, bob).andExpect(status().isCreated());

        assertThat(countSubscriptions()).isEqualTo(2);
    }

    @Test
    @DisplayName("시나리오 7 : 세션이 없으면 신청도 조회도 401이다")
    void scenario7_RequiresSession() throws Exception {
        long auctionId = scheduledAuction();

        mockMvc.perform(put(startAlertUri(auctionId)))
                .andExpect(status().isUnauthorized());

        // 조회는 부르는 사람 기준으로 답하므로 인증이 빠지면 답할 대상이 없다
        mockMvc.perform(get(startAlertUri(auctionId)))
                .andExpect(status().isUnauthorized());

        assertThat(countSubscriptions()).isZero();
    }

    @Test
    @DisplayName("시나리오 8 : 없는 경매에 신청하면 404다")
    void scenario8_RejectsAbsentAuction() throws Exception {
        subscribe(ABSENT_AUCTION_ID, alice)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 9 : 신청 여부는 부르는 회원 기준으로 갈린다")
    void scenario9_SubscriptionIsPerViewer() throws Exception {
        long auctionId = scheduledAuction();
        subscribe(auctionId, alice).andExpect(status().isCreated());

        readSubscription(auctionId, alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true));

        // 같은 경매인데도 신청하지 않은 회원에게는 거짓이어야 한다
        readSubscription(auctionId, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
    }

    @Test
    @DisplayName("시나리오 10 : 같은 회원의 동시 신청 두 건도 신청은 하나만 남는다")
    void scenario10_ConcurrentSubscribeLeavesOneRow() throws Exception {
        long auctionId = scheduledAuction();

        // 더블 클릭이 2 vCPU 에서 진짜로 동시에 도는 상황이다.
        // 잠금 없이 exists 만으로 걸렀다면 둘 다 없다고 읽고 둘 다 저장하려 한다.
        List<Boolean> created = concurrently(2,
                () -> auctionStartAlertService.subscribe(auctionId, alice.getId()));

        // 새로 만든 쪽은 정확히 하나, 나머지는 이미 있다고 답한다
        assertThat(created).containsExactlyInAnyOrder(true, false);
        assertThat(countSubscriptions()).isEqualTo(1);
    }

    /** 신청이 가능한 예정 경매 하나 */
    private long scheduledAuction() {
        return rooms.room(seller, STARTS_SOON).create();
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(long auctionId, User user)
            throws Exception {
        return mockMvc.perform(put(startAlertUri(auctionId)).cookie(sessionCookieOf(user)));
    }

    private org.springframework.test.web.servlet.ResultActions readSubscription(long auctionId, User user)
            throws Exception {
        return mockMvc.perform(get(startAlertUri(auctionId)).cookie(sessionCookieOf(user)));
    }

    private String startAlertUri(long auctionId) {
        return "/api/auctions/" + auctionId + "/start-alert";
    }

    // 같은 회원으로 여러 번 불려도 같은 키를 덮어쓸 뿐이라 중복을 걸러낼 필요가 없다
    // 수명은 시더가 기본값(auth.session.ttl)으로 잡는다, 갱신 임계보다 넉넉해 요청이 세션을 건드리지 않는다
    private Cookie sessionCookieOf(User user) {
        String token = "start-alert-token-" + user.getId();
        sessions.seed(token, user.getId(), user.getRole());
        return new Cookie(SessionCookieFactory.COOKIE_NAME, token);
    }

    /**
     * 같은 일을 여러 스레드에서 동시에 시작시킨다
     */
    private <T> List<T> concurrently(int threads, java.util.concurrent.Callable<T> action)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);

        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.await();
                    return action.call();
                }));
            }

            ready.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }

            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long countSubscriptions() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auction_start_alert_subscription", Long.class);
    }

}
