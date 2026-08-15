package com.softeer.race.evaluation.application;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.evaluation.application.dto.command.EvaluationRejectCommand;
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

import static com.softeer.race.notification.domain.NotificationType.EVAL_REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 평가 반려가 판매자 알림으로 이어지는 경로를 실물 트랜잭션 위에서 확인한다.
 * {@link EvaluationApprovedNotificationIntegrationTest}의 반대 판정 쪽이다.
 * <p>
 * <b>{@code @Transactional}을 걸지 않는다.</b> "반려가 롤백되면 알림도 없다"가 검증 대상이라,
 * 테스트가 트랜잭션을 들고 있으면 커밋·롤백 경계 자체가 관측되지 않는다. 정리는 부모의
 * {@code @AfterEach}가 맡는다.
 * <p>
 * <b>Clock을 고정하지 않는다.</b> 픽스처의 세션 만료가 DB의 실제 시각으로 심기므로 앱 Clock만
 * 옮기면 전 시나리오가 401이 된다.
 * <p>
 * 시나리오
 * <ol>
 *   <li>반려가 커밋되면 판매자 알림함에 한 건이 쌓이고, 링크가 사유를 볼 수 있는 상세를 가리킨다</li>
 *   <li>반려한 평가사에게는 가지 않는다</li>
 *   <li>반려가 롤백되면 알림도 남지 않는다</li>
 * </ol>
 */
@DisplayName("평가 반려 알림 통합 테스트")
@Sql("/sql/diagnostic-report-fixture.sql")
class EvaluationRejectedNotificationIntegrationTest extends IntegrationTestSupport {

    /** 601(박평가)에게 배정된 진행 중 신청, 차량 주인은 600(김판매) */
    private static final long EVALUATION_ID = 600L;
    private static final long SELLER_ID = 600L;
    private static final long EVALUATOR_ID = 601L;

    private static final String EVALUATOR_TOKEN = "report-eval-token";
    private static final String REASON = "번호판이 등록된 차량과 일치하지 않습니다.";

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
    @DisplayName("시나리오 1 : 반려가 커밋되면 판매자에게 사유 확인 안내 알림이 쌓인다")
    void scenario1_NotifiesSeller() throws Exception {
        // when
        reject().andExpect(status().isOk());

        // then 1 : 반려 한 번에 한 건이다
        List<NotificationRow> notifications = notificationsOf(SELLER_ID);
        assertThat(notifications).hasSize(1);

        // then 2 : 발행 당시 문구가 보관되고 아직 읽지 않은 상태다
        NotificationRow rejected = notifications.getFirst();
        assertThat(rejected.type()).isEqualTo(EVAL_REJECTED);
        assertThat(rejected.message())
                .isEqualTo("현대 아반떼 CN7 60가6000 차량의 평가가 반려되었습니다. 사유를 확인해 주세요.");
        assertThat(rejected.read()).isFalse();

        // then 3 : 목적지가 그 신청의 상세다. 사유를 내려보내는 화면이 거기 하나뿐이라,
        //          참조가 빠지면 알림이 "사유를 확인하라"고 해놓고 사유가 없는 곳으로 보낸다
        assertThat(rejected.referenceId()).isEqualTo(EVALUATION_ID);
        assertThat(rejected.link()).isEqualTo("/mypage/evaluations/" + EVALUATION_ID);

        // then 4 : 배지가 바로 맞아야 한다
        assertThat(notificationRepository.countUnread(SELLER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 반려한 평가사에게는 가지 않는다")
    void scenario2_DoesNotNotifyEvaluator() throws Exception {
        reject().andExpect(status().isOk());

        // 받는 사람을 차량 주인으로 고른다. 요청자를 그대로 쓰면 평가사가 자기 알림을 받는다
        assertThat(notificationsOf(EVALUATOR_ID)).isEmpty();
    }

    @Test
    @DisplayName("시나리오 3 : 반려가 롤백되면 알림도 남지 않는다")
    void scenario3_RollbackLeavesNothing() {
        // when : 발행을 반려와 같은 트랜잭션에 둔 결정을 여기서 관측한다
        transactionTemplate.execute(status -> {
            evaluationResultService.reject(
                    new EvaluationRejectCommand(EVALUATION_ID, EVALUATOR_ID, REASON));
            status.setRollbackOnly();
            return null;
        });

        // then : 반려가 없던 일이 됐으니 알림도 없어야 한다.
        //        따로 뗐다면 판매자가 상세를 열어 REQUESTED인 신청을 보게 된다
        assertThat(notificationsOf(SELLER_ID)).isEmpty();
        assertThat(statusOf(EVALUATION_ID)).isEqualTo("REQUESTED");
    }

    private ResultActions reject() throws Exception {
        return mockMvc.perform(post("/api/evaluations/" + EVALUATION_ID + "/rejection")
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, EVALUATOR_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}
                        """.formatted(REASON)));
    }

    private List<NotificationRow> notificationsOf(long userId) {
        return notificationRepository.findPage(userId, Long.MAX_VALUE, Limit.of(10));
    }

    private String statusOf(long evaluationId) {
        return jdbcTemplate.queryForObject(
                "select status from evaluation where id = ?", String.class, evaluationId);
    }
}
