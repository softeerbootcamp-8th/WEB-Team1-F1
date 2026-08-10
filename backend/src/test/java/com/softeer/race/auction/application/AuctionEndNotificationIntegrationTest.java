package com.softeer.race.auction.application;

import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.TestClock;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_ENDED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_FAILED;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_SOLD;
import static com.softeer.race.notification.domain.NotificationType.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 경매가 끝났을 때 누가 무엇을 받는지
 * <p>
 * <b>경매는 시더로 세우고 종료는 프로덕션 경로로 부른다.</b> 알림 행을 SQL 로 심으면 "누가 받아야
 * 하는가"를 테스트가 스스로 정해 버려서, 수신자를 잘못 고르는 버그를 잡지 못한다. 최고 입찰자 판정도
 * 시더가 AuctionCloser 를 그대로 부르므로 프로덕션 규칙을 쓴다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 검증 대상에 "종료가 롤백되면 알림도 없다"가 있어서,
 * 테스트가 트랜잭션을 들고 있으면 경매 하나가 한 트랜잭션이라는 전제와 커밋 경계가 관측되지 않는다.
 * 정리는 부모의 {@code @AfterEach} 가 맡는다.
 */
@DisplayName("경매 종료 알림 통합 테스트")
class AuctionEndNotificationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    // 시작 11:00 → 마감 11:20, 기준 시각에는 이미 마감이 지났다
    private static final LocalDateTime STARTED_AT = NOW.minusHours(1);

    private static final long START_PRICE = 30_000_000L;
    private static final long RAISE = 1_000_000L;

    @Autowired
    private AuctionStarter auctionStarter;

    @Autowired
    private AuctionCloser auctionCloser;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DealRepository dealRepository;

    private TransactionTemplate transactionTemplate;

    private User seller;
    private User alice;
    private User bob;

    @Autowired
    void createTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        seller = users.user("박판매", Role.GENERAL);
        alice = users.user("김앨리스", Role.DEALER);
        bob = users.user("이밥", Role.DEALER);
    }

    @Test
    @DisplayName("시나리오 1 : 낙찰로 끝나면 판매자·낙찰자·미낙찰 입찰자가 각각 한 건씩 받는다")
    void scenario1_NotifiesEachParticipantOnce() {
        // given : 앨리스가 먼저 내고 밥이 올렸다, 최신이자 최고가인 밥이 낙찰자다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), alice, START_PRICE)
                .bid(STARTED_AT.plusMinutes(10), bob, START_PRICE + RAISE)
                .closed()
                .create();

        // then 1 : 판매자는 차가 팔렸다는 것을 안다
        assertThat(notificationsOf(seller)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo(AUCTION_SOLD);
            assertThat(row.message()).isEqualTo(AUCTION_SOLD.defaultMessage());
            assertThat(row.read()).isFalse();
        });

        // then 2 : 낙찰자는 낙찰 사실을 안다
        assertThat(notificationsOf(bob)).singleElement()
                .satisfies(row -> assertThat(row.type()).isEqualTo(AUCTION_WON));

        // then 3 : 밀린 입찰자는 종료를 안다
        assertThat(notificationsOf(alice)).singleElement()
                .satisfies(row -> assertThat(row.type()).isEqualTo(AUCTION_ENDED));

        // then 4 : 셋이 서로 다른 문구다, 한 문구로 묶으면 무슨 일이 났는지 알림만 보고 알 수 없다
        assertThat(messagesOf(seller, bob, alice)).doesNotHaveDuplicates();

        // then 5 : 판매자와 밀린 입찰자는 경매 화면을 가리킨다, 거기서 할 일이 결과 확인뿐이다
        assertThat(linksOf(seller, alice)).containsOnly("/auctions/" + auctionId);

        // then 6 : 낙찰자만 거래 화면을 가리킨다, 결과 확인이 끝나면 경매방에는 볼 것이 없고
        // 실제로 해야 할 일(구매 확정)은 거래 화면에 있다
        long dealId = dealRepository.findAll().getFirst().getId();
        assertThat(linksOf(bob)).containsOnly("/deals/" + dealId);
    }

    @Test
    @DisplayName("시나리오 2 : 낙찰자는 종료 알림을 받지 않는다")
    void scenario2_WinnerIsExcludedFromEndedNotification() {
        // given
        rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), alice, START_PRICE)
                .bid(STARTED_AT.plusMinutes(10), bob, START_PRICE + RAISE)
                .closed()
                .create();

        // then : 미낙찰 조회에서 낙찰자를 빼지 않으면 낙찰과 종료를 두 건 받는다
        assertThat(notificationsOf(bob))
                .extracting(NotificationRow::type)
                .containsExactly(AUCTION_WON);
    }

    @Test
    @DisplayName("시나리오 3 : 같은 경매에 여러 번 입찰한 사람도 한 건만 받는다")
    void scenario3_RepeatedBidderGetsOneNotification() {
        // given : 앨리스가 두 번 입찰했고, 마지막을 올린 밥이 낙찰자다
        rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(2), alice, START_PRICE)
                .bid(STARTED_AT.plusMinutes(4), bob, START_PRICE + RAISE)
                .bid(STARTED_AT.plusMinutes(6), alice, START_PRICE + RAISE * 2)
                .bid(STARTED_AT.plusMinutes(8), bob, START_PRICE + RAISE * 3)
                .closed()
                .create();

        // then : 입찰 횟수만큼 쌓이면 같은 문구가 알림함을 채워 나머지 알림이 묻힌다
        assertThat(notificationsOf(alice)).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 4 : 입찰 없이 끝나면 판매자만 유찰 알림을 받는다")
    void scenario4_NotifiesOnlySellerWhenNoBid() {
        // given : 입찰이 0건이다
        rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .closed()
                .create();

        // then 1 : 판매자는 낙찰과 다른 문구로 유찰을 받는다, 유찰이면 다시 등록해야 한다
        assertThat(notificationsOf(seller)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo(AUCTION_FAILED);
            assertThat(row.message()).isEqualTo(AUCTION_FAILED.defaultMessage());
        });

        // then 2 : 유찰은 입찰이 없었다는 뜻이라 받을 사람이 판매자뿐이다
        assertThat(countRows("notification")).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 5 : 종료를 감싼 트랜잭션이 롤백되면 알림도 종료도 남지 않는다")
    void scenario5_RollbackLeavesNothing() {
        // given : 마감은 지났고 종료 확정만 남았다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), alice, START_PRICE)
                .create();

        // 시작 전이 없이는 종료 확정이 상태 검사에 막힌다
        TestClock.INSTANCE.runAt(STARTED_AT, () -> auctionStarter.start(auctionId));

        // when : 종료를 확정한 뒤 그 트랜잭션이 실패한다
        Throwable thrown = catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    auctionCloser.close(auctionId);
                    throw new IllegalStateException("종료 이후 업무 실패");
                }));

        // then 1 : 알림만 남지 않는다
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(countRows("notification")).isZero();

        // then 2 : 종료도 되돌아가 다음 주기에 다시 잡힌다
        // 발행이 별도 트랜잭션으로 새어 나가면 여기서 알림만 살아남는다
        assertThat(statusOf(auctionId)).isEqualTo("IN_PROGRESS");
    }

    private List<NotificationRow> notificationsOf(User user) {
        return notificationRepository.findPage(user.getId(), Long.MAX_VALUE, Limit.of(10));
    }

    private List<String> messagesOf(User... receivers) {
        return rowsOf(receivers).map(NotificationRow::message).toList();
    }

    private List<String> linksOf(User... receivers) {
        return rowsOf(receivers).map(NotificationRow::link).toList();
    }

    private Stream<NotificationRow> rowsOf(User... receivers) {
        return Arrays.stream(receivers).flatMap(receiver -> notificationsOf(receiver).stream());
    }

    private Long countRows(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Long.class);
    }

    private String statusOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select status from auction where id = ?", String.class, auctionId);
    }
}
