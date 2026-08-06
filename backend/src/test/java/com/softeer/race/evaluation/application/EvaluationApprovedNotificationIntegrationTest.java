package com.softeer.race.evaluation.application;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static com.softeer.race.notification.domain.NotificationType.EVAL_APPROVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 평가 승인이 판매자 알림으로 이어지는 경로를 실물 트랜잭션 위에서
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> "제출이 롤백되면 알림도 없다"가 검증 대상이라,
 * 테스트가 트랜잭션을 들고 있으면 커밋·롤백 경계 자체가 관측되지 않는다. 정리는 부모의
 * {@code @AfterEach} 가 맡는다. 같은 이유로 {@code WelcomeNotificationIntegrationTest} 도
 * 트랜잭션 없이 돈다.
 * <p>
 * <b>Clock 을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB 의 실제 시각으로 심기므로 앱 Clock 만
 * 옮기면 전 시나리오가 401 이 된다.
 * <p>
 * 시나리오
 * <ol>
 *   <li>제출이 커밋되면 판매자 알림함에 등록 안내 한 건이 쌓이고, 링크가 그 신청을 가리킨다</li>
 *   <li>제출한 평가사에게는 가지 않는다</li>
 *   <li>제출이 롤백되면 알림도 남지 않는다</li>
 *   <li>재제출하면 한 건이 더 쌓인다</li>
 * </ol>
 */
@DisplayName("평가 승인 알림 통합 테스트")
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationApprovedNotificationIntegrationTest extends IntegrationTestSupport {

    /** 601(박평가)에게 배정된 진행 중 신청, 차량 주인은 600(김판매) */
    private static final long EVALUATION_ID = 600L;
    private static final long SELLER_ID = 600L;
    private static final long EVALUATOR_ID = 601L;

    private static final String EVALUATOR_TOKEN = "report-eval-token";

    // 테스트 설정의 aws.s3.cdn-base-url과 같아야 한다, 다르면 전부 UNMANAGED_DOCUMENT_URL로 떨어진다
    private static final String CDN_BASE_URL = "https://cdn.test.local";
    private static final String IMAGE_URL =
            CDN_BASE_URL + "/images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final String NEW_DOCUMENT_URL =
            CDN_BASE_URL + "/documents/2026/08/aaaaaaaa-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    @Autowired
    private EvaluationResultService evaluationResultService;

    @Autowired
    private NotificationRepository notificationRepository;

    private TransactionTemplate transactionTemplate;

    @Autowired
    void createTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("시나리오 1 : 제출이 커밋되면 판매자에게 등록 안내 알림이 쌓인다")
    void scenario1_NotifiesSeller() throws Exception {
        // when
        submit(DOCUMENT_URL).andExpect(status().isOk());

        // then 1 : 제출 한 번에 한 건이다
        List<NotificationRow> notifications = notificationsOf(SELLER_ID);
        assertThat(notifications).hasSize(1);

        // then 2 : 발행 당시 문구가 보관되고 아직 읽지 않은 상태다
        NotificationRow approved = notifications.getFirst();
        assertThat(approved.type()).isEqualTo(EVAL_APPROVED);
        assertThat(approved.message()).isEqualTo(EVAL_APPROVED.defaultMessage());
        assertThat(approved.read()).isFalse();

        // then 3 : 목적지가 결과 화면이 아니라 등록 화면이고, 그 신청을 달고 간다.
        //          참조가 빠지면 화면이 어느 차량을 등록할지 알 수 없다
        assertThat(approved.referenceId()).isEqualTo(EVALUATION_ID);
        assertThat(approved.link()).isEqualTo("/sell/auction-post?evaluationId=" + EVALUATION_ID);

        // then 4 : 배지가 바로 맞아야 한다
        assertThat(notificationRepository.countUnread(SELLER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 제출한 평가사에게는 가지 않는다")
    void scenario2_DoesNotNotifyEvaluator() throws Exception {
        // when
        submit(DOCUMENT_URL).andExpect(status().isOk());

        // then : 받는 사람을 차량 주인으로 고른다. 제출자를 그대로 쓰면 평가사가 자기 알림을 받는다
        assertThat(notificationsOf(EVALUATOR_ID)).isEmpty();
    }

    @Test
    @DisplayName("시나리오 3 : 제출이 롤백되면 알림도 남지 않는다")
    void scenario3_RollbackLeavesNothing() {
        // when : 발행을 제출과 같은 트랜잭션에 둔 결정을 여기서 관측한다
        transactionTemplate.execute(status -> {
            evaluationResultService.submit(command(DOCUMENT_URL));
            status.setRollbackOnly();
            return null;
        });

        // then : 승인이 없던 일이 됐으니 승인 알림도 없어야 한다.
        //        따로 뗐다면 판매자가 등록 화면에 들어와 진단 전 차량을 보게 된다
        assertThat(notificationsOf(SELLER_ID)).isEmpty();
        assertThat(statusOf(EVALUATION_ID)).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("시나리오 4 : 재제출하면 한 건이 더 쌓인다")
    void scenario4_ResubmitNotifiesAgain() throws Exception {
        // given
        submit(DOCUMENT_URL).andExpect(status().isOk());

        // when : 잘못 올린 진단서를 고치는 흐름이다
        submit(NEW_DOCUMENT_URL).andExpect(status().isOk());

        // then : 상태는 이미 APPROVED라 바뀌지 않지만 시세나 사진이 달라졌을 수 있어 다시 알린다
        assertThat(notificationsOf(SELLER_ID)).hasSize(2);
    }

    private ResultActions submit(String documentUrl) throws Exception {
        return mockMvc.perform(put("/api/evaluations/" + EVALUATION_ID + "/result")
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, EVALUATOR_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "mileage": %d,
                          "estimatedPrice": %d,
                          "imageUrls": ["%s"],
                          "diagnosticReportUrl": "%s"
                        }
                        """.formatted(MILEAGE, ESTIMATED_PRICE, IMAGE_URL, documentUrl)));
    }

    private static EvaluationResultSubmitCommand command(String documentUrl) {
        return new EvaluationResultSubmitCommand(EVALUATION_ID, EVALUATOR_ID,
                MILEAGE, ESTIMATED_PRICE, List.of(IMAGE_URL), documentUrl);
    }

    private List<NotificationRow> notificationsOf(long userId) {
        return notificationRepository.findPage(userId, Long.MAX_VALUE, Limit.of(10));
    }

    private String statusOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select status from evaluation where id = ?", String.class, evaluationId);
    }
}
