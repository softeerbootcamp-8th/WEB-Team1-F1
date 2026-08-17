package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 평가 결과 제출의 동시성
 * <p>
 * 담당자 한 명만 호출하는 흐름이라 경합이 없어 보이지만, <b>상대가 남이 아니라 자기 자신</b>이다.
 * 같은 평가사가 버튼을 두 번 누르거나 탭 두 개에서 보내면 두 요청이 겹친다.
 * <p>
 * <p>
 * <b>{@code @Transactional}을 붙이지 않는다.</b> 별 스레드가 보내는 요청은 테스트 트랜잭션을
 * 물려받지 않아, 롤백 방식으로 두면 심어 둔 데이터를 보지 못한다. 정리는
 * {@code IntegrationTestSupport}의 {@code @AfterEach}가 한다.
 */
@DisplayName("평가 결과 제출 동시성 통합 테스트")
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationResultConcurrencyIntegrationTest extends IntegrationTestSupport {

    /**
     * 601에게 배정된 진행 중 신청
     */
    private static final long EVALUATION_ID = 600L;
    private static final long VEHICLE_ID = 600L;
    private static final String EVALUATOR_TOKEN = "report-eval-token";

    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String OTHER_DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";


    // 세션만 Redis 에 살아 @Sql 이 함께 심지 못한다, 짝이 되는 세션을 여기서 심는다
    @BeforeEach
    void seedSessions() {
        SessionFixture.diagnosticReport(sessions);
    }

    @Test
    @DisplayName("같은 평가사가 동시에 두 번 제출해도 진단서는 한 건만 남는다")
    void serializesConcurrentFirstSubmissions() throws Exception {
        List<Integer> statuses = submitConcurrently(DOCUMENT_URL, OTHER_DOCUMENT_URL);

        // 뒤에 잠금을 얻은 쪽은 앞이 만든 진단서를 보고 교체로 넘어간다.
        // 잠금이 없으면 그 쪽이 insert 를 시도해 unique 제약으로 500 이 된다
        assertThat(statuses).containsExactly(200, 200);
        assertThat(reportCount()).isEqualTo(1);

        // 마지막에 쓴 쪽의 주소가 남는다. 둘 중 어느 쪽인지는 순서에 달려 있어 고정하지 않는다
        assertThat(fileUrl()).isIn(DOCUMENT_URL, OTHER_DOCUMENT_URL);
    }

    @Test
    @DisplayName("이미 제출된 결과에 동시 재제출이 들어와도 한 건만 남는다")
    void serializesConcurrentResubmissions() throws Exception {
        // given : 먼저 한 번 제출해 둔다
        submit(DOCUMENT_URL).andReturn();

        // when
        List<Integer> statuses = submitConcurrently(OTHER_DOCUMENT_URL, OTHER_DOCUMENT_URL);

        // then : 둘 다 교체 경로를 타므로 행이 늘지 않는다
        assertThat(statuses).containsExactly(200, 200);
        assertThat(reportCount()).isEqualTo(1);
        assertThat(fileUrl()).isEqualTo(OTHER_DOCUMENT_URL);
    }

    /**
     * 두 요청을 같은 순간에 쏜다. sleep 으로 시점을 맞추면 느린 러너에서 간헐적으로 깨지므로
     * 래치로 조율한다 — 배정 동시성 테스트와 같은 방식이다.
     */
    private List<Integer> submitConcurrently(String... documentUrls) throws Exception {
        int threads = documentUrls.length;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (String documentUrl : documentUrls) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    fire.await();
                    statuses.add(submit(documentUrl).andReturn().getResponse().getStatus());
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

        return statuses;
    }

    private ResultActions submit(String documentUrl) throws Exception {
        return mockMvc.perform(put("/api/evaluations/" + EVALUATION_ID + "/result")
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, EVALUATOR_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "mileage": 45000,
                          "estimatedPrice": 21500000,
                          "imageUrls": ["%s"],
                          "diagnosticReportUrl": "%s",
                          "keywords": ["ACCIDENT_FREE", "UNDERBODY_INTACT"]
                        }
                        """.formatted(IMAGE_URL, documentUrl)));
    }

    private int reportCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from vehicle where id = ? and diagnostic_report_url is not null",
                Integer.class, VEHICLE_ID);
    }

    private String fileUrl() {
        return jdbcTemplate.queryForObject(
                "select diagnostic_report_url from vehicle where id = ?",
                String.class, VEHICLE_ID);
    }
}
