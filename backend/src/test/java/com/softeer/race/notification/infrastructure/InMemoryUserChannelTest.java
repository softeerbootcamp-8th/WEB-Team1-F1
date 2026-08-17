package com.softeer.race.notification.infrastructure;

import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.NotificationDeliveryMetrics;
import com.softeer.race.notification.application.UserSubscriber;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// 연결 하나가 구독 하나이고, 채널은 실린 내용을 보지 않고 나르기만 한다
// 경매방 채널과 달리 구독 수를 물어볼 수 없으므로, 걷혔는지는 "다음에 찔러 보는지"로 확인한다
class InMemoryUserChannelTest {

    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    // 창이 좁아 한 번으로는 못 잡는다, 반복 횟수가 곧 검출력이다
    private static final int RACE_ROUNDS = 10_000;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final InMemoryUserChannel channel = new InMemoryUserChannel(
            new NotificationDeliveryMetrics(meterRegistry));

    @Test
    @DisplayName("구독 등록과 해제가 현재 열린 연결 수에 반영된다")
    void subscriptionChangesConnectionGauge() {
        FakeSubscriber subscriber = new FakeSubscriber();

        channel.subscribe(USER, subscriber);
        assertThat(connectionGauge()).isEqualTo(1.0);

        channel.unsubscribe(USER, subscriber);
        assertThat(connectionGauge()).isZero();
    }

    @Test
    @DisplayName("알림 전송의 시도와 성공 및 소요 시간이 구독 단위로 기록된다")
    void recordsSuccessfulNotificationSend() {
        channel.subscribe(USER, new FakeSubscriber());
        channel.subscribe(USER, new FakeSubscriber());

        channel.send(USER, push());

        assertThat(sendCount("attempt")).isEqualTo(2.0);
        assertThat(sendCount("success")).isEqualTo(2.0);
        assertThat(sendCount("failure")).isZero();
        assertThat(meterRegistry.get("notification.sse.send.duration")
                .tag("event", "notification")
                .timer()
                .count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 실패로 기록되고 열린 연결 수에서 빠진다")
    void recordsFailedNotificationSend() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnectOnSend();
        channel.subscribe(USER, broken);

        channel.send(USER, push());

        assertThat(sendCount("attempt")).isEqualTo(1.0);
        assertThat(sendCount("success")).isZero();
        assertThat(sendCount("failure")).isEqualTo(1.0);
        assertThat(connectionGauge()).isZero();
    }

    @Test
    @DisplayName("회원의 모든 구독이 같은 알림을 받는다")
    void everySubscriptionOfTheUserReceivesTheSamePush() {
        FakeSubscriber phone = new FakeSubscriber();
        FakeSubscriber desktop = new FakeSubscriber();
        channel.subscribe(USER, phone);
        channel.subscribe(USER, desktop);

        NotificationPush push = push();
        channel.send(USER, push);

        assertThat(phone.received).containsExactly(push);
        assertThat(desktop.received).containsExactly(push);
    }

    @Test
    @DisplayName("다른 회원의 구독에는 가지 않는다")
    void pushDoesNotLeakToOtherUsers() {
        FakeSubscriber other = new FakeSubscriber();
        channel.subscribe(OTHER_USER, other);
        channel.subscribe(USER, new FakeSubscriber());

        channel.send(USER, push());

        assertThat(other.received).isEmpty();
    }

    @Test
    @DisplayName("접속하지 않은 회원에게 보내도 터지지 않는다")
    void sendingToAbsentUserIsSafe() {
        Throwable thrown = catchThrowable(() -> channel.send(USER, push()));

        assertThat(thrown).isNull();
    }

    @Test
    @DisplayName("같은 구독을 두 번 등록해도 하나다")
    void subscribeIsIdempotent() {
        FakeSubscriber subscriber = new FakeSubscriber();

        channel.subscribe(USER, subscriber);
        channel.subscribe(USER, subscriber);
        channel.send(USER, push());

        // 두 번 등록됐다면 같은 알림을 두 번 받는다
        assertThat(subscriber.received).hasSize(1);
    }

    @Test
    @DisplayName("해제한 구독은 더 받지 않는다")
    void unsubscribedSubscriptionStopsReceiving() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(USER, leaving);

        channel.unsubscribe(USER, leaving);
        channel.send(USER, push());

        assertThat(leaving.received).isEmpty();
    }

    @Test
    @DisplayName("같은 구독을 두 번 해제해도 결과가 같다")
    void unsubscribeIsIdempotent() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(USER, leaving);

        channel.unsubscribe(USER, leaving);
        Throwable thrown = catchThrowable(() -> channel.unsubscribe(USER, leaving));

        assertThat(thrown).isNull();
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 걷어낸다")
    void closedSubscriptionIsRemovedAfterSend() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(USER, broken);

        channel.send(USER, push());
        // 걷혔으면 청소가 찔러 볼 대상에서도 빠져 있다
        channel.sweepClosed();

        assertThat(broken.pings).isZero();
    }

    @Test
    @DisplayName("닫힌 구독이 있어도 나머지는 알림을 받는다")
    void openSubscriptionReceivesDespiteClosedPeer() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        FakeSubscriber alive = new FakeSubscriber();
        channel.subscribe(USER, broken);
        channel.subscribe(USER, alive);

        NotificationPush push = push();
        channel.send(USER, push);

        assertThat(alive.received).containsExactly(push);
    }

    @Test
    @DisplayName("모두 닫힌 회원에게 다시 보내도 터지지 않는다")
    void sendToDrainedUserIsSafe() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(USER, broken);

        channel.send(USER, push());
        Throwable thrown = catchThrowable(() -> channel.send(USER, push()));

        assertThat(thrown).isNull();
    }

    @Test
    @DisplayName("찔러 보기 전에는 살아 있어 보이던 구독도 걷어낸다")
    void sweepDetectsSilentlyClosedSubscription() {
        FakeSubscriber silent = new FakeSubscriber();
        silent.closeOnPing();
        channel.subscribe(USER, silent);

        channel.sweepClosed();
        // 첫 청소에서 닫힘이 드러나 걷혔으면 두 번째에는 찔러 보지 않는다
        channel.sweepClosed();

        assertThat(silent.pings).isEqualTo(1);
    }

    @Test
    @DisplayName("살아 있는 구독은 청소해도 남는다")
    void sweepKeepsOpenSubscription() {
        FakeSubscriber alive = new FakeSubscriber();
        channel.subscribe(USER, alive);

        channel.sweepClosed();
        channel.sweepClosed();

        assertThat(alive.pings).isEqualTo(2);
    }

    @Test
    @DisplayName("건수는 회원의 모든 구독에 간다")
    void unreadCountReachesEverySubscriptionOfTheUser() {
        FakeSubscriber phone = new FakeSubscriber();
        FakeSubscriber desktop = new FakeSubscriber();
        channel.subscribe(USER, phone);
        channel.subscribe(USER, desktop);

        channel.sendUnreadCount(USER, 3L);

        // 한 화면에서 읽은 것이 나머지 화면에 퍼지는 통로다
        assertThat(phone.unreadCounts).containsExactly(3L);
        assertThat(desktop.unreadCounts).containsExactly(3L);
    }

    @Test
    @DisplayName("건수는 다른 회원의 구독에는 가지 않는다")
    void unreadCountDoesNotLeakToOtherUsers() {
        FakeSubscriber other = new FakeSubscriber();
        channel.subscribe(OTHER_USER, other);
        channel.subscribe(USER, new FakeSubscriber());

        channel.sendUnreadCount(USER, 3L);

        assertThat(other.unreadCounts).isEmpty();
    }

    @Test
    @DisplayName("접속하지 않은 회원에게 건수를 보내도 터지지 않는다")
    void sendingUnreadCountToAbsentUserIsSafe() {
        Throwable thrown = catchThrowable(() -> channel.sendUnreadCount(USER, 3L));

        assertThat(thrown).isNull();
    }

    @Test
    @DisplayName("건수를 보내다 닫힌 구독도 걷어내면서 응답까지 끝낸다")
    void closedSubscriptionIsEndedAfterSendingUnreadCount() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(USER, broken);

        channel.sendUnreadCount(USER, 3L);

        // 보내는 갈래마다 정리를 따로 두면 한 곳만 빠뜨려도 그 응답이 만료까지 살아남는다
        assertThat(broken.closes).isEqualTo(1);
        channel.sweepClosed();
        assertThat(broken.pings).isZero();
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 걷어내면서 응답까지 끝낸다")
    void closedSubscriptionIsEndedAfterSend() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(USER, broken);

        channel.send(USER, push());

        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않는다.
        // 상대는 연결이 살아 있다고 믿고 기다리므로 다시 붙지도 않는다
        assertThat(broken.closes).isEqualTo(1);
    }

    @Test
    @DisplayName("청소에서 드러난 구독도 응답까지 끝낸다")
    void silentlyClosedSubscriptionIsEndedAfterSweep() {
        FakeSubscriber silent = new FakeSubscriber();
        silent.closeOnPing();
        channel.subscribe(USER, silent);

        channel.sweepClosed();

        assertThat(silent.closes).isEqualTo(1);
    }

    @Test
    @DisplayName("닫힌 구독을 끝내도 같은 회원의 살아 있는 구독은 그대로다")
    void endingBrokenSubscriptionKeepsPeerOpen() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        FakeSubscriber alive = new FakeSubscriber();
        channel.subscribe(USER, broken);
        channel.subscribe(USER, alive);

        channel.send(USER, push());

        // 한 탭의 전송 실패가 다른 탭의 연결을 끊으면 안 된다
        assertThat(alive.closes).isZero();
        assertThat(alive.isOpen()).isTrue();
    }

    @Test
    @DisplayName("해제는 응답을 끝내지 않는다")
    void unsubscribeDoesNotEndTheResponse() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(USER, leaving);

        channel.unsubscribe(USER, leaving);

        // 해제를 부르는 쪽은 이미 끝난 응답의 콜백이거나, 아직 시작도 안 한 구독의 롤백이다
        assertThat(leaving.closes).isZero();
    }

    @Test
    @DisplayName("마지막 구독이 빠지는 순간 들어온 구독은 유실되지 않는다")
    void arrivingSubscriptionSurvivesLastDeparture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int lost = 0;

        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                FakeSubscriber leaving = new FakeSubscriber();
                FakeSubscriber arriving = new FakeSubscriber();
                channel.subscribe(USER, leaving);

                // 마지막 해제와 새 등록을 같은 순간에 풀어 준다
                CyclicBarrier gate = new CyclicBarrier(2);
                Future<?> departure = pool.submit(() -> {
                    await(gate);
                    channel.unsubscribe(USER, leaving);
                });
                Future<?> arrival = pool.submit(() -> {
                    await(gate);
                    channel.subscribe(USER, arriving);
                });
                departure.get();
                arrival.get();

                // 들어온 쪽은 받아야 한다, 못 받으면 맵에서 떨어져 나간 집합에 들어간 것이다
                channel.send(USER, push());
                if (arriving.received.isEmpty()) {
                    lost++;
                }
                channel.unsubscribe(USER, arriving);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(lost).isZero();
    }

    private static void await(CyclicBarrier gate) {
        try {
            gate.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
    }

    private double connectionGauge() {
        return meterRegistry.get("notification.sse.connections").gauge().value();
    }

    private double sendCount(String outcome) {
        return meterRegistry.get("notification.sse.sends")
                .tag("event", "notification")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    // 채널은 알림을 나르기만 하고 안을 들여다보지 않으므로, 같은 객체가 갔는지만 확인하면 된다
    private static NotificationPush push() {
        return new NotificationPush(
                new NotificationRow(1L, NotificationType.AUCTION_WON, "낙찰되었습니다.", false, 7L,
                        LocalDateTime.of(2026, 8, 4, 12, 0)),
                1L);
    }

    // 실제 SSE 연결은 상대가 끊어도 써 보기 전까지는 살아 있는 것으로 보인다
    private static final class FakeSubscriber implements UserSubscriber {

        private final List<NotificationPush> received = new ArrayList<>();
        private final List<Long> unreadCounts = new ArrayList<>();
        private boolean open = true;
        private boolean closeOnPing;
        private boolean disconnectOnSend;
        private int pings;
        private int closes;

        // 상대가 끊긴 상태로 만든다, 서버가 응답을 끝내는 close() 와는 방향이 반대다
        void disconnect() {
            open = false;
        }

        void closeOnPing() {
            closeOnPing = true;
        }

        void disconnectOnSend() {
            disconnectOnSend = true;
        }

        @Override
        public void send(NotificationPush push) {
            if (!open) {
                return;
            }
            received.add(push);

            if (disconnectOnSend) {
                open = false;
            }
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
        public void close() {
            open = false;
            closes++;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
