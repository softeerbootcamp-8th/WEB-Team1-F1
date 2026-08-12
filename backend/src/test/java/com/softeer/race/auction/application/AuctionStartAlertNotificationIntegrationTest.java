package com.softeer.race.auction.application;

import com.softeer.race.notification.domain.NotificationRepository;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.softeer.race.notification.domain.NotificationType.AUCTION_STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신청해 둔 사람에게 경매 시작이 실제로 도착하는지
 * <p>
 * <b>발송을 직접 부르지 않고 {@code advanceAuctions()} 를 부른다.</b> 시작 알림은 상태 전이 뒤에 붙은
 * 단계라, 전이와 함께 한 주기로 돌 때만 실제와 같다. 발송 메서드만 따로 부르면 "전이 뒤에 둔다"는
 * 배치가 검증되지 않는다.
 * <p>
 * <b>신청도 SQL 이 아니라 프로덕션 경로로 만든다.</b> 신청 가능 시각 판정을 통과한 신청만 표에 남아야
 * 하는데, SQL 로 심으면 그 규칙을 우회한 행으로 발송을 검증하게 된다. 상한 시나리오만 예외다 —
 * 100건을 넘기는 데 필요한 회원 수가 커서 거기서는 표를 직접 채운다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 검증 대상이 "한 경매가 한 트랜잭션"과 "커밋되면 신청이
 * 사라진다"라서, 테스트가 트랜잭션을 들고 있으면 커밋 경계가 관측되지 않는다.
 */
@DisplayName("경매 시작 알림 발송 통합 테스트")
class AuctionStartAlertNotificationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);

    /** 신청을 받아 둘 예정 경매의 시작 시각 */
    private static final LocalDateTime STARTS_AT = NOW.plusMinutes(10);

    /** 경매 진행 20분, 이 시각에는 마감도 지나 한 주기에서 시작과 종료가 연달아 일어난다 */
    private static final LocalDateTime AFTER_CLOSING = STARTS_AT.plusMinutes(25);

    /** AuctionProgressScheduler.ALERT_BATCH_LIMIT 과 같아야 이 시나리오가 성립한다 */
    private static final int BATCH_LIMIT = 100;

    private static final int BULK_SUBSCRIBERS = BATCH_LIMIT + 30;

    @Autowired
    private AuctionProgressScheduler scheduler;

    @Autowired
    private AuctionStartAlertService auctionStartAlertService;

    @Autowired
    private NotificationRepository notificationRepository;

    private User seller;
    private User alice;
    private User bob;

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        seller = users.user("박판매", Role.GENERAL);
        alice = users.user("김앨리스", Role.DEALER);
        bob = users.user("이밥", Role.DEALER);
    }

    @Test
    @DisplayName("시나리오 1 : 경매가 시작되면 신청자 전원이 한 건씩 받고 신청은 사라진다")
    void scenario1_NotifiesEverySubscriberOnce() {
        // given : 시작 전에 둘이 신청해 뒀다
        long auctionId = subscribedAuction(alice, bob);

        // when : 시작 시각이 되어 한 주기가 돈다
        advanceAt(STARTS_AT);

        // then 1 : 문구에 차량명이 박혀 있고 아직 안 읽은 상태다
        assertThat(notificationsOf(alice)).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo(AUCTION_STARTED);
            assertThat(row.message())
                    .isEqualTo("아반떼 CN7 경매가 시작되었습니다. 지금 입찰할 수 있습니다.");
            assertThat(row.read()).isFalse();
        });

        // then 2 : 누른 사람이 할 일은 입찰이고 그건 경매방에서만 한다
        assertThat(notificationsOf(alice)).extracting(NotificationRow::link)
                .containsOnly("/auctions/" + auctionId);

        // then 3 : 신청한 사람은 전원이 받는다
        assertThat(notificationsOf(bob)).hasSize(1);

        // then 4 : 신청하지 않은 판매자는 받지 않는다, 받을 사람은 신청 표가 정한다
        assertThat(notificationsOf(seller)).isEmpty();
        assertThat(countStartedNotifications()).isEqualTo(2);

        // then 5 : 처리한 신청은 지운다, 남겨 두면 다음 주기에 다시 뽑힌다
        assertThat(countSubscriptions()).isZero();
    }

    @Test
    @DisplayName("시나리오 2 : 주기를 여러 번 돌려도 알림이 늘지 않는다")
    void scenario2_DispatchIsIdempotent() {
        long auctionId = subscribedAuction(alice, bob);

        advanceAt(STARTS_AT);
        advanceAt(STARTS_AT);
        advanceAt(STARTS_AT);

        // 신청 삭제가 발송과 같은 트랜잭션에 없으면 여기서 건수가 늘어난다
        assertThat(countStartedNotifications()).isEqualTo(2);
        assertThat(notificationsOf(alice)).hasSize(1);
        assertThat(auctionId).isPositive();
    }

    @Test
    @DisplayName("시나리오 3 : 아직 시작하지 않은 경매의 신청은 알림도 없고 신청도 남는다")
    void scenario3_KeepsScheduledSubscriptionUntouched() {
        subscribedAuction(alice, bob);

        // when : 시작 시각 전이라 전이도 발송도 일어날 것이 없다
        advanceAt(NOW);

        // then 1 : 예정 시각 도달이 아니라 실제 시작이 기준이다
        assertThat(countStartedNotifications()).isZero();

        // then 2 : 지우지 않고 기다린다, 시작하면 그때 보낸다
        assertThat(countSubscriptions()).isEqualTo(2);
    }

    @Test
    @DisplayName("시나리오 4 : 한 주기에 시작과 종료가 연달아 처리된 경매는 알림 없이 신청만 정리된다")
    void scenario4_CleansUpWithoutNotifyingWhenAlreadyEnded() {
        // given : 서버가 오래 멈췄다 살아난 상황이다.
        // 한 주기에서 예약 → 진행 → 종료가 연달아 처리되고, 발송 단계에 도달했을 때 이미 끝나 있다
        subscribedAuction(alice);

        advanceAt(AFTER_CLOSING);

        // then 1 : 끝난 경매를 방금 시작했다고 알리지 않는다
        assertThat(countStartedNotifications()).isZero();
        assertThat(notificationsOf(alice)).isEmpty();

        // then 2 : 보내지 않더라도 신청은 지운다, 안 지우면 이 행은 영구히 남는다
        assertThat(countSubscriptions()).isZero();

        // then 3 : 종료 자체는 정상 처리돼 판매자에게 유찰 알림이 간다, 정리가 종료를 막지 않는다
        assertThat(notificationsOf(seller)).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 5 : 상한을 넘는 신청은 다음 주기로 이어지고 총합은 신청자 수와 같다")
    void scenario5_SplitsAcrossTicksWithoutLossOrDuplication() {
        long auctionId = rooms.room(seller, STARTS_AT).create();
        bulkSubscribe(auctionId, BULK_SUBSCRIBERS);

        // when 1 : 첫 주기는 상한만큼만 처리한다.
        // 상한이 없으면 신청자가 많은 경매 하나가 한 주기를 통째로 쓴다
        advanceAt(STARTS_AT);

        assertThat(countStartedNotifications()).isEqualTo(BATCH_LIMIT);
        assertThat(countSubscriptions()).isEqualTo(BULK_SUBSCRIBERS - BATCH_LIMIT);

        // when 2 : 남은 신청은 다음 주기가 이어서 처리한다
        advanceAt(STARTS_AT);

        // then 1 : 유실도 중복도 없다
        assertThat(countStartedNotifications()).isEqualTo(BULK_SUBSCRIBERS);
        assertThat(countSubscriptions()).isZero();

        // then 2 : 한 사람이 두 번 받은 경우가 없다
        assertThat(usersWithDuplicateStartedNotification()).isZero();
    }

    @Test
    @DisplayName("시나리오 6 : 발송이 끝나면 신청 여부 조회가 거짓이 된다")
    void scenario6_SubscriptionLookupIsFalseAfterDispatch() {
        long auctionId = subscribedAuction(alice);

        assertThat(auctionStartAlertService.isSubscribed(auctionId, alice.getId())).isTrue();

        advanceAt(STARTS_AT);

        // 신청 행을 지우는 방식이 만드는 의도된 결과다.
        // 시작 뒤에는 신청 화면 자체가 없어 사용자에게 보이지 않는다
        assertThat(auctionStartAlertService.isSubscribed(auctionId, alice.getId())).isFalse();
    }

    /** 예정 경매를 세우고 주어진 회원들이 프로덕션 경로로 신청하게 한다 */
    private long subscribedAuction(User... subscribers) {
        long auctionId = rooms.room(seller, STARTS_AT).create();

        for (User subscriber : subscribers) {
            auctionStartAlertService.subscribe(auctionId, subscriber.getId());
        }

        return auctionId;
    }

    /**
     * 상한을 넘기는 데 필요한 회원과 신청을 표에 직접 채운다
     * <p>
     * 100건을 넘기려면 회원이 그만큼 필요해 시더로 만들면 시나리오 하나가 저장 130번을 낸다.
     * 여기서 검증하는 것은 신청 규칙이 아니라 상한 처리라, 신청은 결과 상태만 맞으면 된다.
     */
    private void bulkSubscribe(long auctionId, int count) {
        List<Object[]> rows = new ArrayList<>();
        for (int serial = 1; serial <= count; serial++) {
            rows.add(new Object[]{
                    "bulk" + serial,
                    "bulk" + serial + "@race.dev",
                    "대량" + serial,
                    "0109%07d".formatted(serial),
                    NOW,
                    NOW});
        }

        jdbcTemplate.batchUpdate("""
                insert into users
                    (username, email, password, real_name, phone, role, created_at, updated_at)
                values (?, ?, 'pw', ?, ?, 'DEALER', ?, ?)
                """, rows);

        jdbcTemplate.update("""
                        insert into auction_start_alert_subscription
                            (auction_id, user_id, created_at, updated_at)
                        select ?, id, ?, ? from users where username like 'bulk%'
                        """,
                auctionId, NOW, NOW);

        assertThat(countSubscriptions()).isEqualTo(count);
    }

    /** 그 시각에 멈춘 채로 한 주기를 돌린다 */
    private void advanceAt(LocalDateTime now) {
        fixClockAt(now);
        scheduler.advanceAuctions();
    }

    private List<NotificationRow> notificationsOf(User user) {
        return notificationRepository.findPage(user.getId(), Long.MAX_VALUE, Limit.of(10));
    }

    private Long countStartedNotifications() {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification where type = ?", Long.class, AUCTION_STARTED.name());
    }

    private Long usersWithDuplicateStartedNotification() {
        return jdbcTemplate.queryForObject("""
                        select count(*) from (
                            select user_id from notification
                            where type = ?
                            group by user_id
                            having count(*) > 1
                        ) duplicated
                        """,
                Long.class, AUCTION_STARTED.name());
    }

    private Long countSubscriptions() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auction_start_alert_subscription", Long.class);
    }
}
