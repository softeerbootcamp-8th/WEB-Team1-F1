package com.softeer.race.notification.application;

import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 알림 발행부터 열려 있는 구독에 닿기까지를 실물 트랜잭션 위에서
 * <p>
 * <b>구독은 대역을 쓴다.</b> 브라우저 연결은 우리가 관리하지 않는 바깥 자원이고, 조용히 끊긴 상황이
 * MockMvc 로 재현되지 않는다. 채널·이벤트·DB 는 실물 그대로다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 이 테스트가 검증하는 것이 "커밋 뒤에 전달된다"인데,
 * 테스트에 트랜잭션을 걸면 커밋이 일어나지 않아 전달 자체가 관측되지 않는다. 정리는 부모의
 * {@code @AfterEach} 가 맡는다.
 */
@DisplayName("알림 실시간 전달 통합 테스트")
class NotificationPushIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 12, 0);

    // 낙찰 알림이 가리킬 거래, 링크 조립만 확인하므로 실제 거래가 없어도 된다
    private static final long DEAL_ID = 7L;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Autowired
    private NotificationStreamService notificationStreamService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserChannel userChannel;

    private TransactionTemplate transactionTemplate;

    // 구독은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다, 건 것을 모아 두었다가 끝나고 뺀다
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
    @DisplayName("시나리오 1 : 발행한 알림이 구독 중인 회원에게 건수와 함께 도착한다")
    void scenario1_DeliversPublishedNotification() {
        // given
        long userId = user();
        RecordingSubscriber subscriber = join(userId);

        // when
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

        // then 1 : 저장된 내용이 그대로 실려 온다
        assertThat(subscriber.received).hasSize(1);
        NotificationRow delivered = subscriber.received.getFirst().notification();
        assertThat(delivered.message()).isEqualTo(AUCTION_WON.defaultMessage());
        assertThat(delivered.read()).isFalse();
        assertThat(delivered.createdAt()).isEqualTo(NOW);

        // then 2 : 종류와 참조로 조립한 이동 주소가 함께 온다
        assertThat(delivered.link()).isEqualTo("/deals/" + DEAL_ID);

        // then 3 : 배지에 그대로 쓸 건수가 실려 온다
        assertThat(subscriber.received.getFirst().unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 커밋 전에는 나가지 않고, 커밋된 뒤에 나간다")
    void scenario2_WaitsForCommit() {
        // given
        long userId = user();
        RecordingSubscriber subscriber = join(userId);

        // when : 발행을 감싼 트랜잭션이 아직 커밋되지 않았다
        transactionTemplate.executeWithoutResult(status -> {
            notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

            // then 1 : 저장은 됐지만 아직 확정이 아니라 내보내지 않는다
            // 여기서 밀면 회원이 알림을 보고 목록을 열었을 때 그 알림이 없다
            assertThat(subscriber.received).isEmpty();
        });

        // then 2 : 커밋된 뒤에 도착한다
        assertThat(subscriber.received).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 3 : 발행한 트랜잭션이 롤백되면 저장도 전달도 없다")
    void scenario3_RollbackDeliversNothing() {
        // given
        long userId = user();
        RecordingSubscriber subscriber = join(userId);

        // when : 알림을 발행한 업무가 뒤이어 실패한다
        Throwable thrown = catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);
                    throw new IllegalStateException("업무 실패");
                }));

        // then : 화면에만 남는 알림이 생기지 않는다
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(subscriber.received).isEmpty();
        assertThat(notificationRepository.countUnread(userId)).isZero();
    }

    @Test
    @DisplayName("시나리오 4 : 탭을 두 개 열어 둔 회원은 두 구독 모두 받는다")
    void scenario4_DeliversToEverySubscriptionOfTheUser() {
        // given
        long userId = user();
        RecordingSubscriber phone = join(userId);
        RecordingSubscriber desktop = join(userId);

        // when
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

        // then
        assertThat(phone.received).hasSize(1);
        assertThat(desktop.received).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 5 : 접속하지 않은 회원에게 발행해도 실패하지 않고 알림은 남는다")
    void scenario5_KeepsNotificationForAbsentUser() {
        // given : 구독이 하나도 없다
        long userId = user();

        // when
        Throwable thrown =
                catchThrowable(() -> notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID));

        // then : 보낼 곳이 없는 것은 실패가 아니다, 다음 접속의 조회가 진실을 준다
        assertThat(thrown).isNull();
        assertThat(notificationRepository.countUnread(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 6 : 구독을 열면 안 읽은 건수를 한 번 받는다")
    void scenario6_SendsUnreadCountOnSubscribe() {
        // given : 자리를 비운 사이 알림이 세 건 쌓였다
        long userId = user();
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

        // when
        RecordingSubscriber subscriber = join(userId);

        // then 1 : 배지를 맞출 건수가 연결 직후 한 번 온다
        assertThat(subscriber.unreadCounts).containsExactly(3L);

        // then 2 : 끊긴 사이의 알림을 되짚어 보내지는 않는다, 내용은 목록 조회로 본다
        assertThat(subscriber.received).isEmpty();
    }

    @Test
    @DisplayName("시나리오 7 : 다른 회원의 구독에는 가지 않는다")
    void scenario7_DoesNotLeakToOtherUsers() {
        // given
        long userId = user();
        long otherId = user();
        RecordingSubscriber other = join(otherId);

        // when
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

        // then
        assertThat(other.received).isEmpty();
        assertThat(other.unreadCounts).containsExactly(0L);
    }

    @Test
    @DisplayName("시나리오 8 : 실려 오는 건수에 방금 발행한 알림이 포함된다")
    void scenario8_UnreadCountIncludesTheNewNotification() {
        // given : 안 읽은 알림이 두 건 있다
        long userId = user();
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);
        RecordingSubscriber subscriber = join(userId);

        // when
        notificationPublisher.publish(userId, AUCTION_WON, DEAL_ID);

        // then : 발행 트랜잭션 안에서 세므로 방금 저장한 건이 빠지지 않는다
        assertThat(subscriber.received.getFirst().unreadCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("시나리오 9 : 조용히 끊긴 구독은 청소가 걷어낸다")
    void scenario9_SweepRemovesSilentlyClosedSubscription() {
        // given : 알리지 않고 사라진 연결이다, 찔러 봐야 드러난다
        long userId = user();
        RecordingSubscriber gone = join(userId);
        gone.closeOnPing();

        // when
        notificationStreamService.sweepClosedSubscriptions();
        notificationStreamService.sweepClosedSubscriptions();

        // then : 첫 청소에서 걷혔으므로 두 번째는 찔러 보지 않는다
        assertThat(gone.pings).isEqualTo(1);
    }

    private long user() {
        return users.user("김알림", Role.GENERAL).getId();
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

    // 실제 SSE 연결은 상대가 끊어도 써 보기 전까지는 살아 있는 것으로 보인다
    private static final class RecordingSubscriber implements UserSubscriber {

        private final List<NotificationPush> received = new ArrayList<>();
        private final List<Long> unreadCounts = new ArrayList<>();
        private boolean open = true;
        private boolean closeOnPing;
        private int pings;

        void closeOnPing() {
            closeOnPing = true;
        }

        @Override
        public void send(NotificationPush push) {
            if (!open) {
                return;
            }
            received.add(push);
        }

        @Override
        public void sendUnreadCount(long unreadCount) {
            if (!open) {
                return;
            }
            unreadCounts.add(unreadCount);
        }

        @Override
        public void ping() {
            pings++;

            if (closeOnPing) {
                open = false;
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
