package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * 평가사 배정을 컨트롤러에서 DB까지
 * <p>
 * 1. 목록 — 배정 대기 건만, 방문일 임박순으로 나오는지
 * <p>
 * 2. 연락처 노출선 — 목록에는 없고 배정 응답에만 있는지. 이 흐름에서 개인정보가 새는 지점이 여기다
 * <p>
 * 3. 수락 — 담당이 확정되고 상태는 REQUESTED 로 남는지, 목록에서 빠지는지
 * <p>
 * 4. 최초 1명 — 두 번째 평가사의 수락이 앞의 배정을 덮어쓰지 않는지
 * <p>
 * 5. 건수 상한 없음 — 같은 평가사가 같은 날짜의 여러 신청을 맡을 수 있는지
 * <p>
 * 6. 상태 — 평가가 끝난 신청은 ALREADY_ASSIGNED 가 아니라 NOT_ASSIGNABLE 인지
 * <p>
 * 7. 인증과 인가 — 비로그인과 비평가사 요청을 구분해 막고 역할 변경을 즉시 반영하는지
 * <p>
 * 8. 동시성 — 비관적 잠금이 필요한지를 실제로 확인한다
 * <p>
 * <b>{@code @Transactional}을 붙이지 않는다.</b> 동시 수락 시나리오가 별 스레드에서 요청을 보내는데
 * 그 스레드는 테스트 트랜잭션을 물려받지 않아, 롤백 방식으로 두면 심어 둔 데이터를 보지 못한다.
 * 정리는 IntegrationTestSupport 의 {@code @AfterEach}가 한다.
 * <p>
 * Clock 을 고정하지 않는다. 배정은 시각을 읽지 않으므로(주입된 Clock 이 없다) 고정할 이유가 없고,
 * 픽스처의 세션 만료는 DB 의 실제 시각으로 심겨 앱 Clock 을 옮기면 오히려 401 이 된다.
 */
@DisplayName("평가사 배정 통합 테스트")
@Sql("/sql/evaluation-assignment-fixture.sql")
class EvaluationAssignmentIntegrationTest extends IntegrationTestSupport {

    private static final String KIM_TOKEN = "assign-kim-raw-token";
    private static final String LEE_TOKEN = "assign-lee-raw-token";
    private static final String PARK_TOKEN = "assign-park-raw-token";

    private static final long KIM_ID = 500L;
    private static final long LEE_ID = 501L;
    /** 평가사가 아닌 일반 회원이자, 픽스처 차량들의 판매자 */
    private static final long PARK_ID = 502L;

    /** 8월 20일 방문. 아래 SAME_DATE_EVALUATION 과 방문일이 같다 */
    private static final long WAITING_EVALUATION = 520L;
    /** WAITING_EVALUATION 과 같은 날짜의 다른 신청 */
    private static final long SAME_DATE_EVALUATION = 521L;
    /** 8월 25일 방문. 목록에서 마지막에 온다 */
    private static final long LATER_EVALUATION = 522L;
    /** 평가가 끝난 건(APPROVED, 이평가 배정) */
    private static final long FINISHED_EVALUATION = 523L;

    private static final String CONTACT_PHONE = "01011112222";

    @Test
    @DisplayName("시나리오 1 : 배정 대기 목록은 아직 수락되지 않은 건만 방문일 임박순으로 돌려준다")
    void scenario1_ListsOnlyWaitingOrderedByVisitDate() throws Exception {
        assignable(KIM_TOKEN)
                .andExpect(status().isOk())
                // 평가가 끝난 523은 빠진다. 상태와 evaluator 두 조건을 함께 걸었기 때문이다
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.evaluations[0].evaluationId").value(WAITING_EVALUATION))
                .andExpect(jsonPath("$.evaluations[1].evaluationId").value(SAME_DATE_EVALUATION))
                // 방문일이 뒤인 건이 마지막이다
                .andExpect(jsonPath("$.evaluations[2].evaluationId").value(LATER_EVALUATION))
                .andExpect(jsonPath("$.evaluations[2].visitDate").value("2026-08-25"))
                // 진단 전 차량이라 제원은 있고 주행거리는 없다
                .andExpect(jsonPath("$.evaluations[0].plateNumber").value("12가3456"))
                .andExpect(jsonPath("$.evaluations[0].manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$.evaluations[0].modelYear").value(2021))
                .andExpect(jsonPath("$.evaluations[0].visitAddress").value("서울 성동구 왕십리로 83"));
    }

    /**
     * 이 흐름에서 개인정보가 새는 지점. 목록은 평가사 전원이 보므로 연락처가 담기면 결국
     * 배정받지 않을 사람들에게까지 판매자 전화번호가 뿌려진다.
     */
    @Test
    @DisplayName("시나리오 2 : 연락처는 목록에 없고 배정에 성공한 뒤에만 나간다")
    void scenario2_ContactPhoneOnlyAfterAssignment() throws Exception {
        assignable(KIM_TOKEN)
                .andExpect(jsonPath("$.evaluations[0].contactPhone").doesNotExist())
                // 목록에 담기지 않은 다른 값들도 함께 고정한다
                .andExpect(jsonPath("$.evaluations[0].mileage").doesNotExist())
                .andExpect(jsonPath("$.evaluations[0].estimatedPrice").doesNotExist());

        assign(WAITING_EVALUATION, KIM_TOKEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactPhone").value(CONTACT_PHONE));
    }

    @Test
    @DisplayName("시나리오 3 : 수락하면 담당이 확정되고 상태는 REQUESTED로 남는다")
    void scenario3_AssignsEvaluatorAndKeepsStatus() throws Exception {
        assign(WAITING_EVALUATION, KIM_TOKEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationId").value(WAITING_EVALUATION))
                .andExpect(jsonPath("$.plateNumber").value("12가3456"))
                .andExpect(jsonPath("$.visitDate").value("2026-08-20"))
                // 배정과 평가 결과가 다른 축이라는 결정을 고정한다
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        Map<String, Object> row = rowOf(WAITING_EVALUATION);
        assertThat(row.get("evaluator_id")).isEqualTo(KIM_ID);
        assertThat(row.get("status")).isEqualTo("REQUESTED");

        // 배정된 건은 대기 목록에서 빠진다
        assignable(KIM_TOKEN).andExpect(jsonPath("$.evaluations.length()").value(2));
    }

    /**
     * "최초 수락 1명"이 실제로 지켜지는 지점.
     */
    @Test
    @DisplayName("시나리오 4 : 이미 배정된 신청은 다른 평가사가 수락할 수 없다")
    void scenario4_SecondEvaluatorIsRejected() throws Exception {
        assign(WAITING_EVALUATION, KIM_TOKEN).andExpect(status().isCreated());

        assign(WAITING_EVALUATION, LEE_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_ALREADY_ASSIGNED"));

        // 앞의 배정이 덮어써지지 않았다
        assertThat(rowOf(WAITING_EVALUATION).get("evaluator_id")).isEqualTo(KIM_ID);
    }

    /**
     * 한 평가사가 맡는 건수에 상한을 두지 않기로 한 결정을 고정한다. 방문 시각을 모르는 상태에서
     * 날짜로 막으면 오전 · 오후로 나뉘어 실제로 겹치지 않는 일정까지 거부된다.
     */
    @Test
    @DisplayName("시나리오 5 : 같은 평가사가 같은 날짜의 여러 신청을 맡을 수 있다")
    void scenario5_AllowsMultipleVisitsOnSameDate() throws Exception {
        assign(WAITING_EVALUATION, KIM_TOKEN).andExpect(status().isCreated());
        assign(SAME_DATE_EVALUATION, KIM_TOKEN).andExpect(status().isCreated());

        assertThat(rowOf(WAITING_EVALUATION).get("evaluator_id")).isEqualTo(KIM_ID);
        assertThat(rowOf(SAME_DATE_EVALUATION).get("evaluator_id")).isEqualTo(KIM_ID);
    }

    /**
     * 평가가 끝난 건은 배정도 되어 있지만 ALREADY_ASSIGNED 가 아니다. 목록을 다시 봐도 돌아오지
     * 않는 건이라 화면이 안내할 말이 다르고, 그래서 두 코드를 갈라 뒀다.
     */
    @Test
    @DisplayName("시나리오 6 : 평가가 끝난 신청은 NOT_ASSIGNABLE이다")
    void scenario6_FinishedEvaluationIsNotAssignable() throws Exception {
        assign(FINISHED_EVALUATION, KIM_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_ASSIGNABLE"));

        assertThat(rowOf(FINISHED_EVALUATION).get("evaluator_id")).isEqualTo(LEE_ID);
    }

    @Test
    @DisplayName("시나리오 7 : 없는 신청은 404다")
    void scenario7_MissingEvaluationIsNotFound() throws Exception {
        assign(9999L, KIM_TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 8 : 평가사가 아닌 회원은 목록 조회와 수락이 모두 403이다")
    void scenario8_NonEvaluatorIsForbidden() throws Exception {
        assignable(PARK_TOKEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        assign(WAITING_EVALUATION, PARK_TOKEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        assertThat(rowOf(WAITING_EVALUATION).get("evaluator_id")).isNull();
    }

    @Test
    @DisplayName("시나리오 9 : 세션이 살아 있어도 DB 역할 변경은 다음 요청에 즉시 반영된다")
    void scenario9_RoleChangeTakesEffectOnNextRequest() throws Exception {
        assignable(PARK_TOKEN).andExpect(status().isForbidden());

        jdbcTemplate.update("update users set role = 'EVALUATOR' where id = ?", PARK_ID);

        assignable(PARK_TOKEN).andExpect(status().isOk());
    }

    // 두 핸들러의 @LoginUser 선언으로 인증이 실제로 요구되는지. 구현체가 아니라 인터페이스에
    // 붙이면 AuthInterceptor 에 보이지 않아 조용히 공개 API 가 된다
    @Test
    @DisplayName("시나리오 10 : 세션 쿠키가 없으면 401이다")
    void scenario10_RequiresSession() throws Exception {
        mockMvc.perform(get("/api/evaluations/assignable")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/evaluations/" + WAITING_EVALUATION + "/assignment"))
                .andExpect(status().isUnauthorized());

        assertThat(rowOf(WAITING_EVALUATION).get("evaluator_id")).isNull();
    }

    /**
     * 잠금이 없으면 두 요청이 같은 {@code evaluator == null}을 읽고 둘 다 통과한 뒤 둘 다 쓴다.
     * 나중 쓰기가 앞의 배정을 덮어써 "먼저 수락한 한 명"이 지켜지지 않는다.
     * <p>
     * EvaluationRepository.findByIdForUpdate 에서 {@code @Lock}을 떼면 이 테스트가 깨진다.
     * 잠금이 필요하다는 근거가 여기 있다.
     */
    @Test
    @DisplayName("시나리오 11 : 두 평가사가 동시에 수락해도 한 명만 배정된다")
    void scenario11_SerializesConcurrentAssignments() throws Exception {
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        // sleep 으로 시점을 맞추면 느린 러너에서 간헐적으로 깨진다, 래치로 조율한다
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (String token : List.of(KIM_TOKEN, LEE_TOKEN)) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    fire.await();
                    statuses.add(assign(WAITING_EVALUATION, token)
                            .andReturn().getResponse().getStatus());
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

        // 뒤에 잠금을 얻은 쪽은 이미 채워진 evaluator 를 보고 거절된다
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(rowOf(WAITING_EVALUATION).get("evaluator_id")).isIn(KIM_ID, LEE_ID);
    }

    // ================= 요청 =================

    private ResultActions assignable(String rawToken) throws Exception {
        return mockMvc.perform(get("/api/evaluations/assignable").cookie(sessionCookie(rawToken)));
    }

    private ResultActions assign(long evaluationId, String rawToken) throws Exception {
        return mockMvc.perform(post("/api/evaluations/" + evaluationId + "/assignment")
                .cookie(sessionCookie(rawToken)));
    }

    private static Cookie sessionCookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }

    // ================= 조회 =================

    private Map<String, Object> rowOf(long evaluationId) {
        return jdbcTemplate.queryForMap(
                "select evaluator_id, status from evaluation where id = ?", evaluationId);
    }
}
