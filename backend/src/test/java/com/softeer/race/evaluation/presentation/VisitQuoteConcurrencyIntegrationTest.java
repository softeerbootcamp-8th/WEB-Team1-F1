package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 방문견적 접수의 동시성
 * <p>
 * 접수는 "진행 중인 신청이 있는지 확인 → 없으면 만든다" 순서인데 이 둘이 원자적이지 않다. 거의 같은
 * 순간에 들어온 두 요청이 모두 "없음"을 읽으면 둘 다 접수되고, 한 차량에 평가사 방문이 두 번 잡힌다.
 * 나중에 신청한 사람은 막혔다는 안내를 받지 못한 채 접수된 것으로 알게 된다.
 * <p>
 * 그래서 {@code PlateNumberLock}의 행을 잠근 뒤 판정과 저장을 한다. 이 테스트가 확인하는 것은
 * <b>실제 DB 잠금</b>이라 Testcontainers의 MySQL 위에서만 의미가 있다.
 * <p>
 * <b>{@code @Transactional}을 붙이지 않는다.</b> 별 스레드가 보내는 요청은 테스트 트랜잭션을
 * 물려받지 않아, 롤백 방식으로 두면 심어 둔 데이터를 보지 못한다. 정리는
 * {@code IntegrationTestSupport}의 {@code @AfterEach}가 한다.
 * <p>
 * 순서 결정 자체({@code 잠금 → 판정})는 {@code VisitQuoteServiceTest}가 고정한다. 여기서는
 * 그 순서가 실제 DB 위에서 무엇을 보장하는지만 본다.
 */
@DisplayName("방문견적 접수 동시성 통합 테스트")
@Sql({"/sql/vehicle-catalog-fixture.sql", "/sql/visit-quote-fixture.sql"})
class VisitQuoteConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final String RAW_TOKEN = "visit-quote-raw-token";
    private static final String OTHER_RAW_TOKEN = "visit-quote-other-raw-token";

    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String OTHER_PLATE_NUMBER = "34나5678";
    private static final String OTHER_OWNER_NAME = "이서연";
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    // 고정하지 않은 실제 Clock이다, 방문 날짜를 여기서 상대적으로 만든다
    @Autowired
    private Clock clock;

    // 세션만 Redis 에 살아 @Sql 이 함께 심지 못한다, 짝이 되는 세션을 여기서 심는다
    @BeforeEach
    void seedSessions() {
        SessionFixture.visitQuote(sessions);
    }

    /**
     * 잠글 대상 행이 아직 없는 번호판에 세 건이 몰리는 경우다. 두 건이 아니라 세 건인 이유는
     * 잠금 행을 확보하는 방식이 여기서 갈리기 때문이다 — {@code insert ... on duplicate key update}로
     * 확보하면 대기하던 요청들이 공유 잠금에서 배타 잠금으로 승격하려다 서로 물려 데드락이 나는데,
     * 그 현상은 두 건으로는 재현되지 않는다.
     */
    @Test
    @DisplayName("잠금 행이 없는 번호판에 동시 신청이 몰려도 한 건만 접수된다")
    void serializesConcurrentFirstRequests() throws Exception {
        List<Integer> statuses = requestConcurrently(PLATE_NUMBER, OWNER_NAME,
                RAW_TOKEN, OTHER_RAW_TOKEN, RAW_TOKEN);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(2);

        // 차량도 함께 하나여야 한다. 둘이면 거부된 요청의 vehicle insert가 롤백되지 않았다는 뜻이다
        assertThat(countOf("evaluation")).isEqualTo(1);
        assertThat(countOf("vehicle")).isEqualTo(1);
    }

    /**
     * 잠금 행이 이미 있는 경로. 위 시나리오와 코드가 갈라지는 곳은 없지만, 잠금 확보가
     * {@code insert ignore}의 부수 효과에 기대고 있어 행이 있을 때와 없을 때를 모두 밟아 둔다.
     */
    @Test
    @DisplayName("잠금 행이 이미 있어도 동시 신청 중 한 건만 접수된다")
    void serializesConcurrentRequestsOnExistingLockRow() throws Exception {
        jdbcTemplate.update("insert into plate_number_lock (plate_number) values (?)", PLATE_NUMBER);

        List<Integer> statuses = requestConcurrently(PLATE_NUMBER, OWNER_NAME,
                RAW_TOKEN, OTHER_RAW_TOKEN);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(1);
        assertThat(countOf("evaluation")).isEqualTo(1);
        assertThat(countOf("vehicle")).isEqualTo(1);
    }

    /**
     * 잠금이 번호판 단위라는 것. 전역 잠금으로 만들면 이 테스트만 깨지고 나머지는 그대로 통과하는데,
     * 그러면 서로 무관한 차량의 접수까지 한 줄로 세워진다.
     */
    @Test
    @DisplayName("서로 다른 번호판의 동시 신청은 둘 다 접수된다")
    void doesNotBlockDifferentPlates() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);

        submitTo(pool, ready, fire, done, statuses, PLATE_NUMBER, OWNER_NAME, RAW_TOKEN);
        submitTo(pool, ready, fire, done, statuses, OTHER_PLATE_NUMBER, OTHER_OWNER_NAME, OTHER_RAW_TOKEN);

        await(ready, fire, done, pool);

        assertThat(statuses).containsExactly(201, 201);
        assertThat(countOf("evaluation")).isEqualTo(2);
        assertThat(countOf("vehicle")).isEqualTo(2);
    }

    /**
     * 같은 번호판으로 요청들을 같은 순간에 쏜다. sleep 으로 시점을 맞추면 느린 러너에서 간헐적으로
     * 깨지므로 래치로 조율한다 — 평가 결과 제출 동시성 테스트와 같은 방식이다.
     */
    private List<Integer> requestConcurrently(String plateNumber, String ownerName, String... tokens)
            throws Exception {
        int threads = tokens.length;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (String token : tokens) {
            submitTo(pool, ready, fire, done, statuses, plateNumber, ownerName, token);
        }

        await(ready, fire, done, pool);

        return statuses;
    }

    private void submitTo(ExecutorService pool, CountDownLatch ready, CountDownLatch fire,
                          CountDownLatch done, List<Integer> statuses,
                          String plateNumber, String ownerName, String token) {
        pool.submit(() -> {
            try {
                ready.countDown();
                fire.await();
                statuses.add(request(plateNumber, ownerName, token));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                done.countDown();
            }
        });
    }

    private void await(CountDownLatch ready, CountDownLatch fire, CountDownLatch done,
                       ExecutorService pool) throws Exception {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
    }

    private int request(String plateNumber, String ownerName, String token) throws Exception {
        return mockMvc.perform(post("/api/visit-quotes")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber": "%s", "ownerName": "%s",
                                 "visitAddress": "%s", "visitDate": "%s", "contactPhone": "%s"}
                                """.formatted(plateNumber, ownerName, VISIT_ADDRESS,
                                LocalDate.now(clock).plusDays(16), CONTACT_PHONE)))
                .andReturn().getResponse().getStatus();
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Integer.class);
    }
}
