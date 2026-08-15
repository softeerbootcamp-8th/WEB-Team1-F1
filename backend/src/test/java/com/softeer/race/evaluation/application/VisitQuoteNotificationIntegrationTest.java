package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.NotificationStreamService;
import com.softeer.race.notification.application.UserChannel;
import com.softeer.race.notification.application.UserSubscriber;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.softeer.race.notification.domain.NotificationType.EVAL_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 방문견적 접수가 평가사 알림으로 이어지는 경로를 실물 트랜잭션 위에서 확인한다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> "접수가 롤백되면 알림도 없다"와 "커밋 뒤에
 * 전달된다"가 검증 대상이라, 테스트가 트랜잭션을 들고 있으면 그 경계가 관측되지 않는다.
 * 정리는 부모의 {@code @AfterEach} 가 맡는다.
 * <p>
 * 시나리오
 * <ol>
 *   <li>평가사 전원에게 한 건씩 쌓이고, 문구·링크·참조가 배정 대기 목록을 가리킨다</li>
 *   <li>판매자 · 일반 회원 · 딜러에게는 가지 않는다</li>
 *   <li>평가사가 없어도 접수는 성공하고 알림만 없다</li>
 *   <li>접수가 롤백되면 알림도 남지 않는다</li>
 *   <li>중복으로 거부된 신청은 알림을 늘리지 않는다</li>
 *   <li>접속 중인 평가사에게는 커밋 뒤 실시간으로 도착한다</li>
 *   <li>신청 두 건은 문구와 참조로 구분된다</li>
 * </ol>
 */
@DisplayName("방문견적 신청 알림 통합 테스트")
@Sql("/sql/vehicle-catalog-fixture.sql")
class VisitQuoteNotificationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 13, 10, 0);

    /** 카탈로그 201번, 소유자명까지 맞아야 접수된다 */
    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String MODEL = "그랜저 IG";

    /** 카탈로그 202번, 신청 두 건을 구분하는 시나리오가 쓴다 */
    private static final String OTHER_PLATE_NUMBER = "34나5678";
    private static final String OTHER_OWNER_NAME = "이서연";
    private static final String OTHER_MODEL = "쏘렌토";

    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    @Autowired
    private VisitQuoteService visitQuoteService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationStreamService notificationStreamService;

    @Autowired
    private UserChannel userChannel;

    private TransactionTemplate transactionTemplate;

    // 구독은 테이블이 아니라 컨텍스트에 남아 정리 훅이 지워 주지 않는다
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Autowired
    void createTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @AfterEach
    void leaveChannels() {
        subscriptions.forEach(it -> userChannel.unsubscribe(it.userId(), it.subscriber()));
    }

    @Test
    @DisplayName("시나리오 1 : 접수되면 평가사 전원에게 한 건씩 쌓이고 배정 대기 목록을 가리킨다")
    void scenario1_NotifiesEveryEvaluator() {
        // given
        long sellerId = seller();
        long firstEvaluatorId = evaluator();
        long secondEvaluatorId = evaluator();

        // when
        VisitQuoteInfo info = request(sellerId, PLATE_NUMBER, OWNER_NAME);

        // then 1 : 수신자마다 별도 행이다. 방송이면 자리를 비운 평가사가 신청을 놓친다
        assertThat(notificationsOf(firstEvaluatorId)).hasSize(1);
        assertThat(notificationsOf(secondEvaluatorId)).hasSize(1);

        NotificationRow notification = notificationsOf(firstEvaluatorId).getFirst();
        assertThat(notification.type()).isEqualTo(EVAL_REQUESTED);
        assertThat(notification.read()).isFalse();

        // then 2 : 어느 차량의 신청인지 문구로 가려진다
        assertThat(notification.message())
                .isEqualTo("현대 %s %s 차량의 방문견적 신청이 접수되었습니다."
                        .formatted(MODEL, PLATE_NUMBER));

        // then 3 : 담당이 정해지기 전이라 개별 상세가 아니라 배정 대기 목록으로 보낸다.
        //          참조는 어느 신청인지 남기는 값이고 링크에는 쓰이지 않는다
        assertThat(notification.referenceId()).isEqualTo(info.evaluationId());
        assertThat(notification.link()).isEqualTo("/evaluations/assignable");

        // then 4 : 배지가 바로 맞아야 한다
        assertThat(notificationRepository.countUnread(secondEvaluatorId)).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 판매자 · 일반 회원 · 딜러에게는 가지 않는다")
    void scenario2_DoesNotNotifyOtherRoles() {
        // given
        long sellerId = seller();
        long otherGeneralId = users.user("이일반", Role.GENERAL).getId();
        long dealerId = users.user("박딜러", Role.DEALER).getId();
        evaluator();

        // when
        request(sellerId, PLATE_NUMBER, OWNER_NAME);

        // then : 수신자를 역할로 좁힌 결정을 여기서 고정한다
        assertThat(notificationsOf(sellerId)).isEmpty();
        assertThat(notificationsOf(otherGeneralId)).isEmpty();
        assertThat(notificationsOf(dealerId)).isEmpty();
        assertThat(countOfNotifications()).isEqualTo(1);
    }

    // 코드로는 루프가 0회 도는 것뿐이라 조용히 통과하는 지점이다.
    // 나중에 "수신자가 없으면 예외"가 들어오면 이 테스트만 깨진다
    @Test
    @DisplayName("시나리오 3 : 평가사가 한 명도 없어도 접수는 성공하고 알림만 없다")
    void scenario3_SucceedsWithoutEvaluators() {
        // given : 위촉된 평가사가 없는 상태다
        long sellerId = seller();

        // when
        VisitQuoteInfo info = request(sellerId, PLATE_NUMBER, OWNER_NAME);

        // then : 알림을 받을 사람이 없다는 것이 판매자의 신청을 거부할 이유는 아니다
        assertThat(info.evaluationId()).isNotNull();
        assertThat(info.status()).isEqualTo("REQUESTED");
        assertThat(countOfNotifications()).isZero();
    }

    @Test
    @DisplayName("시나리오 4 : 접수가 롤백되면 알림도 남지 않는다")
    void scenario4_RollbackLeavesNothing() {
        // given
        long sellerId = seller();
        long evaluatorId = evaluator();

        // when : 발행을 접수와 같은 트랜잭션에 둔 결정을 여기서 관측한다
        Throwable thrown = catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    visitQuoteService.request(command(sellerId, PLATE_NUMBER, OWNER_NAME));
                    throw new IllegalStateException("접수 실패");
                }));

        // then : 알림만 남으면 평가사가 배정 대기 목록에서 찾을 수 없는 신청을 보게 된다
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(notificationsOf(evaluatorId)).isEmpty();
        assertThat(countOf("evaluation")).isZero();
    }

    @Test
    @DisplayName("시나리오 5 : 중복으로 거부된 신청은 알림을 늘리지 않는다")
    void scenario5_DuplicateRequestAddsNoNotification() {
        // given : 같은 번호판으로 진행 중인 신청이 이미 있다
        long sellerId = seller();
        long evaluatorId = evaluator();
        request(sellerId, PLATE_NUMBER, OWNER_NAME);

        // when
        Throwable thrown = catchThrowable(
                () -> request(sellerId, PLATE_NUMBER, OWNER_NAME));

        // then : 거부된 요청의 알림이 먼저 보이면 목록에 없는 신청을 안내하게 된다
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(notificationsOf(evaluatorId)).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 6 : 접속 중인 평가사에게는 커밋 뒤 실시간으로 도착한다")
    void scenario6_DeliversToConnectedEvaluator() {
        // given : 평가사 한 명은 화면을 열어 두었고 한 명은 자리를 비웠다
        long sellerId = seller();
        long connectedId = evaluator();
        long absentId = evaluator();
        RecordingSubscriber connected = join(connectedId);

        // when
        request(sellerId, PLATE_NUMBER, OWNER_NAME);

        // then 1 : 새 채널을 만들지 않고 기존 회원별 통로로 간다
        assertThat(connected.received).hasSize(1);
        NotificationRow delivered = connected.received.getFirst().notification();
        assertThat(delivered.type()).isEqualTo(EVAL_REQUESTED);
        assertThat(delivered.link()).isEqualTo("/evaluations/assignable");

        // then 2 : 배지에 쓸 건수가 함께 온다
        assertThat(connected.received.getFirst().unreadCount()).isEqualTo(1);

        // then 3 : 자리를 비운 평가사도 저장은 돼 있어 다음 접속의 조회가 진실을 준다
        assertThat(notificationsOf(absentId)).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 7 : 신청 두 건은 문구와 참조로 구분된다")
    void scenario7_DistinguishesRequests() {
        // given
        long sellerId = seller();
        long evaluatorId = evaluator();

        // when : 서로 다른 차량의 신청 두 건이 접수된다
        VisitQuoteInfo first = request(sellerId, PLATE_NUMBER, OWNER_NAME);
        VisitQuoteInfo second = request(sellerId, OTHER_PLATE_NUMBER, OTHER_OWNER_NAME);

        // then : 링크가 같은 목록이므로 문구와 참조가 구분을 맡는다
        List<NotificationRow> notifications = notificationsOf(evaluatorId);
        assertThat(notifications).hasSize(2);
        assertThat(notifications).extracting(NotificationRow::referenceId)
                .containsExactlyInAnyOrder(first.evaluationId(), second.evaluationId());
        assertThat(notifications).extracting(NotificationRow::message)
                .containsExactlyInAnyOrder(
                        "현대 %s %s 차량의 방문견적 신청이 접수되었습니다."
                                .formatted(MODEL, PLATE_NUMBER),
                        "기아 %s %s 차량의 방문견적 신청이 접수되었습니다."
                                .formatted(OTHER_MODEL, OTHER_PLATE_NUMBER));
    }

    private VisitQuoteInfo request(long sellerId, String plateNumber, String ownerName) {
        return visitQuoteService.request(command(sellerId, plateNumber, ownerName));
    }

    private static VisitQuoteCommand command(
            long sellerId, String plateNumber, String ownerName) {
        return new VisitQuoteCommand(sellerId, plateNumber, ownerName,
                VISIT_ADDRESS, LocalDate.of(2026, 8, 20), CONTACT_PHONE);
    }

    private long seller() {
        return users.user("김판매", Role.GENERAL).getId();
    }

    private long evaluator() {
        return users.user("박평가", Role.EVALUATOR).getId();
    }

    private List<NotificationRow> notificationsOf(long userId) {
        return notificationRepository.findPage(userId, Long.MAX_VALUE, Limit.of(10));
    }

    private int countOfNotifications() {
        return countOf("notification");
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject(
                "select count(*) from `" + table + "`", Integer.class);
    }

    // 실제 경로와 같게 스트림 서비스로 등록한다, 연결 직후 건수 전송까지 함께 돈다
    private RecordingSubscriber join(long userId) {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        notificationStreamService.subscribe(userId, subscriber);
        subscriptions.add(new Subscription(userId, subscriber));

        return subscriber;
    }

    private record Subscription(long userId, UserSubscriber subscriber) {
    }

    // 브라우저 연결은 우리가 관리하지 않는 바깥 자원이라 대역을 쓴다, 채널·이벤트·DB는 실물이다
    private static final class RecordingSubscriber implements UserSubscriber {

        private final List<NotificationPush> received = new ArrayList<>();
        private final List<Long> unreadCounts = new ArrayList<>();

        @Override
        public void send(NotificationPush push) {
            received.add(push);
        }

        @Override
        public void sendUnreadCount(long unreadCount) {
            unreadCounts.add(unreadCount);
        }

        @Override
        public void ping() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
