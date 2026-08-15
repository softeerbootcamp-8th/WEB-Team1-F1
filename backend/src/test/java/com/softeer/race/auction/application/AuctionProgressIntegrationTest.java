package com.softeer.race.auction.application;

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
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매 상태 진행 통합 테스트
 * <p>
 * 테스트에서는 race.scheduling.enabled=false 라 배경 스레드가 돌지 않는다.
 * 진입점을 직접 불러 한 주기만 재현한다.
 * <p>
 * 트랜잭션을 걸지 않는다. 걸면 건별 @Transactional 이 테스트 트랜잭션에 합류해
 * 경매 하나가 한 트랜잭션이라는 전제가 깨지고, 커밋 경계를 검증할 수 없다.
 * 정리는 부모의 clearTables 가 맡는다.
 * <p>
 * 실제 시각을 쓰지 않는다. CI 러너(UTC)와 로컬(KST)에서 결과가 갈리면 안 되므로
 * 시계를 고정하고 심어둔 시각과만 비교한다.
 * <p>
 * 경매를 SQL 로 심지 않고 시더로 세운다. 개장·마감 시각을 손으로 쓰면 schedule 이 계산하는 값과
 * 어긋나도 테스트가 통과한다. 여기 오는 경매는 전부 예약 상태이고, 진행 중으로 올리는 것도
 * advanceAuctions 가 할 일이라 미리 만들어 둘 이유가 없다.
 */
@DisplayName("경매 상태 진행 통합 테스트")
@Sql("/sql/bid-increment-bands.sql")
class AuctionProgressIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
    private static final String ALICE_TOKEN = "token-run-alice";

    @Autowired
    private AuctionProgressScheduler scheduler;
    @Autowired
    private AuctionCloser auctionCloser;

    private long endedAuction;    // 마감 1시간 전에 끝났다, 입찰 2건
    private long noBidAuction;    // 마감 지났고 입찰이 없다
    private long liveAuction;     // 마감이 10분 남았다
    private long waitingAuction;  // 시작이 30분 남았다
    private User bob;

    @BeforeEach
    void seed() {
        User seller = users.user("박판매", Role.GENERAL);
        User alice = users.user("김앨리스", Role.GENERAL);
        bob = users.user("이밥", Role.DEALER);

        // 시작 11:00 → 마감 11:20 (schedule 이 시작 + 20분으로 정한다)
        // 앨리스가 먼저 내고 밥이 올렸다, 최신이자 최고가인 밥이 낙찰자다
        endedAuction = rooms.room(seller, NOW.minusHours(1))
                .startPrice(30_000_000L)
                .bid(NOW.minusMinutes(55), alice, 30_000_000L)
                .bid(NOW.minusMinutes(45), bob, 31_000_000L)
                .create();

        noBidAuction = rooms.room(seller, NOW.minusHours(1))
                .startPrice(38_000_000L)
                .create();

        // 시작 11:50 → 마감 12:10, 아직 진행 중이다
        liveAuction = rooms.room(seller, NOW.minusMinutes(10))
                .startPrice(34_000_000L)
                .create();

        waitingAuction = rooms.room(seller, NOW.plusMinutes(30))
                .startPrice(29_000_000L)
                .create();

        giveSession(alice, ALICE_TOKEN);
        fixClockAt(NOW);
    }

    // UserSeeder 는 세션까지 만들지 않는다, 쿠키가 필요한 시나리오 하나 때문에 여기서 심는다
    private void giveSession(User user, String rawToken) {
        sessions.seed(rawToken, user.getId(), user.getRole());
    }

    @Test
    @DisplayName("시나리오 1 : 마감이 지난 경매는 최고 입찰자를 낙찰자로 확정한다")
    void 낙찰_확정() {
        scheduler.advanceAuctions();

        assertThat(statusOf(endedAuction)).isEqualTo("ENDED");
        assertThat(winnerOf(endedAuction)).isEqualTo(bob.getId());
        // 낙찰가는 종료가 새로 정하는 값이 아니라 마지막 입찰이 남긴 현재가 그대로다
        assertThat(currentPriceOf(endedAuction)).isEqualTo(31_000_000L);
    }

    @Test
    @DisplayName("시나리오 2 : 입찰이 없는 경매는 낙찰자 없이 유찰된다")
    void 유찰() {
        scheduler.advanceAuctions();

        assertThat(statusOf(noBidAuction)).isEqualTo("FAILED");
        assertThat(winnerOf(noBidAuction)).isNull();
    }

    @Test
    @DisplayName("시나리오 3 : 아직 때가 되지 않은 경매는 건드리지 않는다")
    void 시각_전_유지() {
        scheduler.advanceAuctions();

        assertThat(statusOf(liveAuction)).isEqualTo("IN_PROGRESS");
        assertThat(statusOf(waitingAuction)).isEqualTo("SCHEDULED");
        assertThat(winnerOf(liveAuction)).isNull();
    }

    // advanceAuctions 가 시작 전이를 먼저 돌리는 이유다.
    // 종료 후보는 IN_PROGRESS 만 고르므로, 예약 상태로 마감을 지난 경매는 시작 전이가
    // 먼저 올려줘야 같은 주기에 닫힌다. 순서를 뒤집으면 한 주기 늦어져 이 단정이 깨진다.
    @Test
    @DisplayName("시나리오 4 : 예약 상태로 마감을 지난 경매도 한 주기에 종료까지 간다")
    void 자기복구() {
        // 심어둔 경매는 전부 예약 상태다, 스케줄러가 오래 멈췄다 재개한 상황과 같다
        assertThat(statusOf(endedAuction)).isEqualTo("SCHEDULED");
        assertThat(statusOf(noBidAuction)).isEqualTo("SCHEDULED");

        scheduler.advanceAuctions();

        assertThat(statusOf(endedAuction)).isEqualTo("ENDED");
        assertThat(statusOf(noBidAuction)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("시나리오 5 : 두 번 돌려도 결과가 달라지지 않는다")
    void 멱등() {
        scheduler.advanceAuctions();
        scheduler.advanceAuctions();

        assertThat(statusOf(endedAuction)).isEqualTo("ENDED");
        assertThat(winnerOf(endedAuction)).isEqualTo(bob.getId());
        assertThat(bidCountOf(endedAuction)).isEqualTo(2);
    }

    // 후보 조회를 건너뛰고 직접 부른다.
    // 잠금 뒤 재판정이 없으면 Auction.close 안의 검사가 IllegalStateException 을 던진다.
    @Test
    @DisplayName("시나리오 6 : 마감 전 경매에 종료를 요청해도 조용히 넘어간다")
    void 잠금후_재판정() {
        // 마감이 10분 남은 경매를 진행 중으로 올려둔다, 종료 요청이 잘못 오는 상황을 만든다
        scheduler.advanceAuctions();
        assertThat(statusOf(liveAuction)).isEqualTo("IN_PROGRESS");

        assertThatCode(() -> auctionCloser.close(liveAuction)).doesNotThrowAnyException();

        assertThat(statusOf(liveAuction)).isEqualTo("IN_PROGRESS");
        assertThat(winnerOf(liveAuction)).isNull();
    }

    @Test
    @DisplayName("시나리오 7 : 종료된 경매에는 입찰이 성립하지 않는다")
    void 종료후_입찰_거절() throws Exception {
        scheduler.advanceAuctions();

        bid(endedAuction, 32_000_000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_LIVE"));

        assertThat(bidCountOf(endedAuction)).isEqualTo(2);
        assertThat(winnerOf(endedAuction)).isEqualTo(bob.getId());
    }

    /**
     * 마감 순간의 입찰과 종료 확정이 같은 경매 행을 두고 경쟁한다.
     * <p>
     * 어느 쪽이 먼저 잠금을 얻어도 결과가 같아야 한다. 종료가 먼저면 입찰은 마감을 지나
     * 거절되고, 입찰이 먼저면 마감 판정에 걸려 거절된 뒤 종료가 이어진다.
     * <p>
     * 입찰이 성립할 수 있는 시각과 종료가 가능한 시각은 서로 배타적이라(now &lt; end 대 now &gt;= end)
     * 승패는 정해져 있다. 여기서 보는 것은 승패가 아니라 <b>경쟁 중에도 낙찰가와 낙찰자가
     * 같은 입찰에서 온다</b>는 것이다. 잠금이나 트랜잭션 경계가 깨지면 입찰 쪽 롤백이
     * 확정된 낙찰을 덮거나, 낙찰자 없이 종료되는 상태가 나온다.
     */
    @Test
    @DisplayName("시나리오 8 : 마감 순간 입찰과 종료가 겹쳐도 낙찰 결과가 흔들리지 않는다")
    void 마감순간_입찰과_종료_경합() throws Exception {
        int bidders = 2;
        int threads = bidders + 1;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        // sleep 으로 시점을 맞추면 느린 러너에서 간헐적으로 깨진다, 래치로 조율한다
        List<Integer> bidStatuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < bidders; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    fire.await();
                    bidStatuses.add(bid(endedAuction, 32_000_000L).andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        pool.submit(() -> {
            try {
                ready.countDown();
                fire.await();
                scheduler.advanceAuctions();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                done.countDown();
            }
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 마감을 지난 시각이라 입찰은 어느 쪽이 먼저 잠금을 얻든 전부 거절된다
        assertThat(bidStatuses).containsOnly(409);
        assertThat(bidCountOf(endedAuction)).isEqualTo(2);
        // 낙찰가와 낙찰자가 같은 입찰(밥의 3,100만원)에서 왔다
        assertThat(statusOf(endedAuction)).isEqualTo("ENDED");
        assertThat(winnerOf(endedAuction)).isEqualTo(bob.getId());
        assertThat(currentPriceOf(endedAuction)).isEqualTo(31_000_000L);
    }

    /**
     * 서버를 여러 대로 늘리면 인스턴스마다 같은 후보를 뽑아 같은 경매에 종료를 요청한다.
     * <p>
     * 스케줄러를 거치지 않고 AuctionCloser 를 직접 부른다. 스케줄러의 try/catch 가 예외를
     * 삼켜버리면 잠금 뒤 재판정을 지워도 이 테스트가 통과하기 때문이다.
     * 재판정이 없으면 뒤에 잠금을 얻은 쪽에서 IllegalStateException 이 올라온다.
     */
    @Test
    @DisplayName("시나리오 9 : 여러 곳에서 동시에 종료를 요청해도 한 번만 확정된다")
    void 동시_종료_요청() throws Exception {
        // 마감 전 시각에 한 번 돌려 진행 중으로 올려둔 뒤, 시계를 마감 후로 옮긴다.
        // 예약 상태로는 종료 대상이 아니라, 진행 중인 경매가 마감을 지나는 실제 흐름을 만든다
        fixClockAt(NOW.minusMinutes(50));
        scheduler.advanceAuctions();
        fixClockAt(NOW);

        int threads = 3;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    fire.await();
                    auctionCloser.close(endedAuction);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failures).isEmpty();
        assertThat(statusOf(endedAuction)).isEqualTo("ENDED");
        assertThat(winnerOf(endedAuction)).isEqualTo(bob.getId());
    }

    private org.springframework.test.web.servlet.ResultActions bid(long auctionId, long amount) throws Exception {
        return mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, ALICE_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %d}".formatted(amount)));
    }

    private String statusOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select status from auction where id = ?", String.class, auctionId);
    }

    private Long winnerOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select winner_id from auction where id = ?", Long.class, auctionId);
    }

    private Long currentPriceOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select current_price from auction where id = ?", Long.class, auctionId);
    }

    private Integer bidCountOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from bid where auction_id = ?", Integer.class, auctionId);
    }
}
