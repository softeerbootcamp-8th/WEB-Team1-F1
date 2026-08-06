package com.softeer.race.user.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.NotificationStreamService;
import com.softeer.race.notification.application.UserChannel;
import com.softeer.race.notification.application.UserSubscriber;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.softeer.race.notification.domain.NotificationType.WELCOME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 가입이 환영 알림을 남기는 경로를 실물 트랜잭션 위에서
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 검증하려는 것 중에 "가입을 감싼 트랜잭션이 롤백되면
 * 알림도 없다"가 있어서, 테스트가 트랜잭션을 들고 있으면 커밋·롤백 경계 자체가 관측되지 않는다.
 * 정리는 부모의 {@code @AfterEach} 가 맡는다.
 */
@DisplayName("가입 환영 알림 통합 테스트")
class WelcomeNotificationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationStreamService notificationStreamService;

    @Autowired
    private UserChannel userChannel;

    private TransactionTemplate transactionTemplate;

    // 구독은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다
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
    @DisplayName("시나리오 1 : 가입이 완료되면 환영 알림 한 건이 쌓인다")
    void scenario1_LeavesWelcomeNotification() {
        // when
        long userId = userService.signUp(signUpCommand("race_kim", "race@race.kr")).id();

        // then 1 : 가입 한 번에 한 건이다
        List<NotificationRow> notifications =
                notificationRepository.findPage(userId, Long.MAX_VALUE, Limit.of(10));
        assertThat(notifications).hasSize(1);

        // then 2 : 발행 당시 문구가 그대로 보관되고, 아직 읽지 않은 상태다
        NotificationRow welcome = notifications.getFirst();
        assertThat(welcome.type()).isEqualTo(WELCOME);
        assertThat(welcome.message()).isEqualTo(WELCOME.defaultMessage());
        assertThat(welcome.read()).isFalse();
        assertThat(welcome.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시나리오 2 : 가리킬 대상이 없어도 이동할 곳이 고정 화면으로 나온다")
    void scenario2_LinksToFixedScreen() {
        // when
        long userId = userService.signUp(signUpCommand("race_kim", "race@race.kr")).id();

        // then : 참조를 싣지 않았는데도 주소가 만들어진다
        // 회원 자신을 참조로 잡지 않은 결정이 여기서 검증된다 — 주소에 회원 식별자가 실리지 않는다
        NotificationRow welcome =
                notificationRepository.findPage(userId, Long.MAX_VALUE, Limit.of(10)).getFirst();
        assertThat(welcome.link()).isEqualTo("/auctions");
    }

    @Test
    @DisplayName("시나리오 3 : 이미 쓰는 이메일이면 가입도 알림도 남지 않는다")
    void scenario3_LeavesNothingWhenSignUpRejected() {
        // given
        User existing = users.user("김기존", Role.GENERAL);

        // when
        assertThatThrownBy(() ->
                userService.signUp(signUpCommand("race_kim", existing.getEmail())))
                .isInstanceOf(BusinessException.class);

        // then : 주인 없는 환영 알림이 생기지 않는다
        assertThat(countRows("notification")).isZero();
        assertThat(countRows("users")).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 4 : 가입을 감싼 트랜잭션이 롤백되면 회원도 알림도 없다")
    void scenario4_RollbackLeavesNothing() {
        // given : 발행이 별도 트랜잭션이면 여기서 알림만 살아남는다
        AtomicLong userId = new AtomicLong();

        // when : 가입 뒤에 그 트랜잭션이 실패한다
        Throwable thrown = catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    userId.set(userService.signUp(signUpCommand("race_kim", "race@race.kr")).id());
                    throw new IllegalStateException("가입 이후 업무 실패");
                }));

        // then : 저장이 한 트랜잭션이라 둘이 함께 사라진다
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(notificationRepository.countUnread(userId.get())).isZero();
        assertThat(countRows("notification")).isZero();
        assertThat(countRows("users")).isZero();
    }

    @Test
    @DisplayName("시나리오 5 : 가입 시점엔 구독이 없어 즉시 전달되지 않고, 다음 연결의 배지에 잡힌다")
    void scenario5_ArrivesAsBadgeOnFirstConnection() {
        // given : 가입은 세션을 발급하지 않아 이 시점에 그 회원의 구독은 있을 수 없다
        long userId = userService.signUp(signUpCommand("race_kim", "race@race.kr")).id();

        // when : 로그인해서 화면을 열면 그때 스트림이 붙는다
        RecordingSubscriber subscriber = join(userId);

        // then 1 : 연결 직후 건수 한 번으로 벨 배지가 맞는다
        assertThat(subscriber.unreadCounts).containsExactly(1L);

        // then 2 : 끊긴 사이의 알림은 되짚어 보내지 않으므로 내용은 오지 않는다, 목록 조회가 진실을 준다
        assertThat(subscriber.pushes).isEmpty();
    }

    private static SignUpCommand signUpCommand(String username, String email) {
        return new SignUpCommand(
                username,
                email,
                "password123",
                "김레이스",
                "010-1234-5678",
                Role.GENERAL);
    }

    private Long countRows(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Long.class);
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

    private static final class RecordingSubscriber implements UserSubscriber {

        private final List<NotificationPush> pushes = new ArrayList<>();
        private final List<Long> unreadCounts = new ArrayList<>();

        @Override
        public void send(NotificationPush push) {
            pushes.add(push);
        }

        @Override
        public void sendUnreadCount(long unreadCount) {
            unreadCounts.add(unreadCount);
        }

        @Override
        public void ping() {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
