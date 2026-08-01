package com.softeer.race.bid.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입찰 접수를 컨트롤러에서 DB까지
 * <p>
 * 1. 성립과 반영 — 첫 입찰이 경매방 현황(현재가·호가창·집계)에 나타나는지
 * <p>
 * 2. 금액 규칙 — 최소 금액과 상승가 배수, 두 거절 사유가 갈리는 지점
 * <p>
 * 3. 단계와 자격 — 저장된 status 가 아니라 서버 시각으로 판정, 판매자·연속 입찰 차단
 * <p>
 * 4. 인증 — 세션 쿠키 없이는 입찰이 불가능한지
 * <p>
 * 5. 마감 연장 — 임계 안 입찰이 마감을 밀고 그 결과가 응답과 방 조회에 함께 나타나는지
 * <p>
 * 6. 동시성과 재전송 — 비관적 락이 필요한지, 멱등 키가 없어도 되는지를 실제로 확인한다
 */
@DisplayName("입찰 접수 통합 테스트")
@Sql({"/sql/bid-increment-bands.sql", "/sql/bid-place-fixture.sql"})
class BidIntegrationTest extends IntegrationTestSupport {

    // 러너 시간대(UTC)와 로컬(KST)에서 결과가 갈리지 않도록 실제 시각을 쓰지 않는다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 0);

    private static final long LIVE_AUCTION = 51L;
    private static final long WAITING_AUCTION = 52L;
    private static final long CLOSING_AUCTION = 53L;

    private static final long SELLER_ID = 51L;
    private static final long ALICE_ID = 52L;

    private static final String SELLER_TOKEN = "token-seller";
    private static final String ALICE_TOKEN = "token-alice";
    private static final String BOB_TOKEN = "token-bob";
    private static final String EXPIRED_TOKEN = "token-expired";

    // 픽스처의 시작가와 그 가격대 구간의 상승가
    private static final long START_PRICE = 24_800_000L;
    private static final long INCREMENT = 50_000L;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @Test
    @DisplayName("시나리오 1 : 첫 입찰은 시작가 그대로 성립하고 경매방 현황에 반영된다")
    void firstBidAtStartPrice() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bidId").isNumber())
                .andExpect(jsonPath("$.amount").value(START_PRICE))
                // 마감 30초 임계 밖의 입찰이라 마감은 그대로다
                .andExpect(jsonPath("$.endAt").value("2026-08-03T21:00:00"))
                .andExpect(jsonPath("$.serverTime").value("2026-08-03T20:45:00"));

        // 방 조회는 bid 테이블을 매번 집계하므로 별도 갱신 없이 나타나야 한다
        room(LIVE_AUCTION, ALICE_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPrice").value(START_PRICE))
                .andExpect(jsonPath("$.bidCount").value(1))
                .andExpect(jsonPath("$.bidderCount").value(1))
                .andExpect(jsonPath("$.recentBids.length()").value(1))
                .andExpect(jsonPath("$.recentBids[0].amount").value(START_PRICE));
    }

    @Test
    @DisplayName("시나리오 2 : 최소 금액에 못 미치면 거절한다")
    void rejectsAmountBelowMinimum() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE - INCREMENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BID_AMOUNT_TOO_LOW"));

        assertThat(bidCount(LIVE_AUCTION)).isZero();
    }

    // 화면이 +버튼만 제공해도 API 는 열려 있다, 임의 금액은 서버가 막아야 한다
    @Test
    @DisplayName("시나리오 3 : 상승가 배수가 아니면 거절한다")
    void rejectsMisalignedAmount() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE + 30_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BID_AMOUNT_NOT_ALIGNED"));

        assertThat(bidCount(LIVE_AUCTION)).isZero();
    }

    // 픽스처의 status 는 전부 SCHEDULED 다, 이 두 케이스가 갈리는 근거는 서버 시각뿐이다
    @Test
    @DisplayName("시나리오 4 : 아직 시작하지 않은 경매는 방이 열려 있어도 거절한다")
    void rejectsBidBeforeStart() throws Exception {
        bid(WAITING_AUCTION, ALICE_TOKEN, 20_000_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_LIVE"));
    }

    @Test
    @DisplayName("시나리오 5 : 판매자는 자기 차량에 입찰할 수 없다")
    void rejectsSellerBid() throws Exception {
        bid(LIVE_AUCTION, SELLER_TOKEN, START_PRICE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));

        assertThat(bidCount(LIVE_AUCTION)).isZero();
    }

    @Test
    @DisplayName("시나리오 6 : 이미 최고가인 사람은 연달아 올릴 수 없다")
    void rejectsSelfOutbid() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE).andExpect(status().isCreated());

        // 금액은 규칙을 만족하지만 직전 입찰자가 본인이라 거절된다
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE + INCREMENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_OUTBID"));

        assertThat(bidCount(LIVE_AUCTION)).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 7 : 세션 없이는 입찰할 수 없다")
    void rejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/bids", LIVE_AUCTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(START_PRICE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));

        bid(LIVE_AUCTION, EXPIRED_TOKEN, START_PRICE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"));

        assertThat(bidCount(LIVE_AUCTION)).isZero();
    }

    // 인증이 본문 검증보다 먼저다, 쿠키가 없으면 금액이 무엇이든 401 이 나간다
    @Test
    @DisplayName("시나리오 8 : 0 이하 금액은 요청 단계에서 걸러진다")
    void rejectsNonPositiveAmount() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    @DisplayName("시나리오 9 : 마감 임박 입찰은 마감을 밀고 응답과 방 조회에 함께 나타난다")
    void extendsDeadlineOnClosingBid() throws Exception {
        // 마감 20:45:10, 고정 시각 20:45:00 이라 잔여 10초. 임계 30초 안이다
        bid(CLOSING_AUCTION, ALICE_TOKEN, 10_000_000)
                .andExpect(status().isCreated())
                // 누적 가산이 아니라 재설정이라 입찰 시각 + 30초다
                .andExpect(jsonPath("$.endAt").value("2026-08-03T20:45:30"));

        room(CLOSING_AUCTION, ALICE_ID)
                .andExpect(jsonPath("$.endAt").value("2026-08-03T20:45:30"))
                .andExpect(jsonPath("$.phase").value("LIVE"));
    }

    /**
     * 비관적 락이 없으면 두 요청이 같은 현재가를 읽고 둘 다 통과해, 같은 금액의 최고가가 두 건 남는다.
     * 두 사람이 같은 금액으로 동시에 들어와도 한 건만 성립해야 한다.
     * <p>
     * findByIdForUpdate 에서 @Lock 을 떼면 이 테스트가 깨진다. 락이 필요하다는 근거가 여기 있다.
     */
    @Test
    @DisplayName("시나리오 10 : 같은 금액이 동시에 들어와도 한 건만 성립한다")
    void serializesConcurrentBids() throws Exception {
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        // sleep 으로 시점을 맞추면 느린 러너에서 간헐적으로 깨진다, 래치로 조율한다
        List<Integer> statuses = Collections.synchronizedList(new java.util.ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (String token : List.of(ALICE_TOKEN, BOB_TOKEN)) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    fire.await();
                    statuses.add(bid(LIVE_AUCTION, token, START_PRICE).andReturn()
                            .getResponse().getStatus());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 뒤에 락을 얻은 쪽은 갱신된 현재가를 보고 최소 금액 미달로 거절된다
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(bidCount(LIVE_AUCTION)).isEqualTo(1);
        assertThat(currentPriceOf(LIVE_AUCTION)).isEqualTo(START_PRICE);
    }

    /**
     * 멱등 키를 두지 않은 근거다.
     * <p>
     * 응답 유실로 클라이언트가 같은 요청을 다시 보내도, 금액이 같으면 이제 최소 금액에 못 미치고
     * 직전 입찰자도 본인이라 두 규칙에 각각 걸린다. 재전송이 성립할 경로가 없다.
     */
    @Test
    @DisplayName("시나리오 11 : 같은 요청이 두 번 도착해도 입찰은 한 번만 성립한다")
    void retransmissionCannotCreateDuplicate() throws Exception {
        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE).andExpect(status().isCreated());

        bid(LIVE_AUCTION, ALICE_TOKEN, START_PRICE)
                .andExpect(status().isConflict());

        assertThat(bidCount(LIVE_AUCTION)).isEqualTo(1);
        assertThat(currentPriceOf(LIVE_AUCTION)).isEqualTo(START_PRICE);
    }

    private ResultActions bid(long auctionId, String rawToken, long amount) throws Exception {
        return mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(amount)));
    }

    // 경매방 조회는 아직 X-User-Id 임시 헤더를 쓴다
    private ResultActions room(long auctionId, long userId) throws Exception {
        return mockMvc.perform(get("/api/auctions/{auctionId}/room", auctionId)
                .header("X-User-Id", userId));
    }

    private static String body(long amount) {
        return "{\"amount\": %d}".formatted(amount);
    }

    private Integer bidCount(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from bid where auction_id = ?", Integer.class, auctionId);
    }

    private Long currentPriceOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select current_price from auction where id = ?", Long.class, auctionId);
    }
}
