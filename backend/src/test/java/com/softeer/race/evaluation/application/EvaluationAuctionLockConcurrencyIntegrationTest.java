package com.softeer.race.evaluation.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softeer.race.auction.application.AuctionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultPatchCommand;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/** 경매 등록과 평가 수정이 같은 차량 잠금으로 직렬화되는지 실제 MySQL에서 확인한다. */
@DisplayName("경매 등록과 평가 수정 동시성 통합 테스트")
@Sql("/sql/evaluation-result-patch-fixture.sql")
class EvaluationAuctionLockConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final long SELLER_ID = 700L;
    private static final long EVALUATOR_ID = 701L;
    private static final long EVALUATION_ID = 700L;
    private static final long VEHICLE_ID = 700L;
    private static final int ORIGINAL_MILEAGE = 45_000;
    private static final int NEW_MILEAGE = 46_000;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private EvaluationResultService evaluationResultService;

    @Autowired
    private TransactionTemplate transactionTemplate;


    // 세션만 Redis 에 살아 @Sql 이 함께 심지 못한다, 짝이 되는 세션을 여기서 심는다
    @BeforeEach
    void seedSessions() {
        SessionFixture.evaluationResultPatch(sessions);
    }

    @Test
    @DisplayName("경매 등록이 차량을 먼저 잠그면 평가 수정은 커밋을 기다린 뒤 거부된다")
    void auctionCreationWinsVehicleLock() throws Exception {
        fixClockAt(NOW);
        CountDownLatch auctionCreated = new CountDownLatch(1);
        CountDownLatch allowAuctionCommit = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> auction = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                auctionService.create(SELLER_ID, VEHICLE_ID, 10_000_000L, NOW.plusHours(2));
                auctionCreated.countDown();
                await(allowAuctionCommit);
            }));

            assertThat(auctionCreated.await(5, SECONDS)).isTrue();

            Future<?> patch = pool.submit(() -> evaluationResultService.patch(
                    new EvaluationResultPatchCommand(
                            EVALUATION_ID, EVALUATOR_ID, NEW_MILEAGE,
                            null, null, null, null)));

            // 경매 트랜잭션이 차량 잠금을 쥔 동안 평가는 경매 없음으로 통과하지 못하고 기다린다
            assertThatThrownBy(() -> patch.get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowAuctionCommit.countDown();
            auction.get(5, SECONDS);

            assertThatThrownBy(() -> get(patch))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode())
                                    .isEqualTo(EvaluationErrorCode.RESULT_LOCKED_BY_AUCTION));
        } finally {
            allowAuctionCommit.countDown();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auction where post_id in "
                        + "(select id from auction_post where vehicle_id = ?)",
                Integer.class, VEHICLE_ID)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select mileage from vehicle where id = ?", Integer.class, VEHICLE_ID))
                .isEqualTo(ORIGINAL_MILEAGE);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("경매 커밋 허용 신호를 기다리다 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("경매 커밋 대기 중 인터럽트", exception);
        }
    }

    private static Object get(Future<?> future) throws Throwable {
        try {
            return future.get(5, SECONDS);
        } catch (ExecutionException exception) {
            throw exception.getCause();
        }
    }
}
