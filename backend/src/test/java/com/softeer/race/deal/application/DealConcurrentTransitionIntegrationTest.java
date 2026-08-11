package com.softeer.race.deal.application;

import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 같은 사람이 같은 요청을 동시에 두 번 보낼 때
 * <p>
 * 단계마다 움직일 수 있는 사람이 한 명뿐이라 두 주체가 겹칠 경로는 없다. 남는 경합은 중복 클릭이고,
 * 순서대로 들어오면 전이 검사가 두 번째를 거른다. <b>이 테스트가 증명하는 것은 그 검사가 통과한
 * 두 요청이 겹쳤을 때다</b> — 비관적 락 없이 낙관적 락만으로 막힌다는 근거가 여기 있다.
 * <p>
 * <b>트랜잭션을 실제로 갈라야 한다.</b> 한 트랜잭션 안에서 같은 거래를 두 번 읽으면 1차 캐시가 같은
 * 인스턴스를 돌려주므로 버전 충돌이 일어나지 않는다. 그래서 스레드마다 트랜잭션을 연다.
 */
@DisplayName("거래 동시 전이 통합 테스트")
class DealConcurrentTransitionIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);
    private static final LocalDateTime STARTED_AT = NOW.minusHours(1);

    private static final long START_PRICE = 30_000_000L;

    // 상대 스레드를 기다리다 영영 멈추지 않게 상한을 둔다
    private static final int LATCH_TIMEOUT_SECONDS = 5;

    @Autowired
    private DealRepository dealRepository;

    private TransactionTemplate transactionTemplate;

    private User seller;
    private User bob;

    @Autowired
    void createTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        seller = users.user("박판매", Role.GENERAL);
        bob = users.user("이밥", Role.DEALER);
    }

    @Test
    @DisplayName("같은 구매 확정 요청이 동시에 두 번 와도 한 번만 반영된다")
    void onlyOneConcurrentTransitionSucceeds() throws Exception {
        // given : 낙찰로 열린 거래 하나
        long dealId = createdDealId();

        // 둘 다 같은 버전을 읽은 뒤에 쓰게 만든다, 이 겹침이 없으면 그냥 순서대로 성공한다
        CountDownLatch bothRead = new CountDownLatch(2);

        Callable<Throwable> transition = () -> catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    var deal = dealRepository.findById(dealId).orElseThrow();

                    bothRead.countDown();
                    awaitQuietly(bothRead);

                    // 둘 다 같은 스냅샷을 읽어서 전이 검사를 통과한다, 여기서는 아무도 걸리지 않는다
                    deal.confirmPurchase(NOW.plusHours(1));
                }));

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();
        try {
            // 성공한 쪽은 null 을 돌려준다, 남는 것이 실패다
            for (Future<Throwable> future : pool.invokeAll(List.of(transition, transition))) {
                Throwable thrown = future.get();
                if (thrown != null) {
                    failures.add(thrown);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // then 1 : 정확히 한쪽만 실패한다
        assertThat(failures).singleElement()
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // then 2 : 반영은 한 번뿐이다, 버전이 두 번 올랐다면 나중 쓰기가 앞의 것을 덮은 것이다
        assertThat(statusOf(dealId)).isEqualTo("SELLER_SUBMIT_PENDING");
        assertThat(versionOf(dealId)).isEqualTo(1L);
    }

    // 낙찰 경로로 거래를 만든다, 거래 행을 직접 심으면 버전 초기값이 프로덕션과 달라질 수 있다
    private long createdDealId() {
        rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), bob, START_PRICE)
                .closed()
                .create();

        return dealRepository.findAll().getFirst().getId();
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("상대 스레드를 기다리다 중단됐다", e);
        }
    }

    private String statusOf(long dealId) {
        return jdbcTemplate.queryForObject("select status from deal where id = ?", String.class, dealId);
    }

    private Long versionOf(long dealId) {
        return jdbcTemplate.queryForObject("select version from deal where id = ?", Long.class, dealId);
    }
}
