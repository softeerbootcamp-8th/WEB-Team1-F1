package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.domain.BidStats;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import com.softeer.race.auctionroom.domain.RoomPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 연결 하나가 구독 하나이고 그 수가 곧 접속자 수다, 채널은 현황 내용을 보지 않고 나르기만 한다
class InMemoryRoomChannelTest {

    private static final long AUCTION = 1L;
    private static final long OTHER_AUCTION = 2L;

    // 창이 좁아 한 번으로는 못 잡는다, 반복 횟수가 곧 검출력이다
    private static final int RACE_ROUNDS = 10_000;

    private final InMemoryRoomChannel channel = new InMemoryRoomChannel();

    @Test
    @DisplayName("구독 수가 곧 접속자 수다")
    void subscribersAreCounted() {
        channel.subscribe(AUCTION, new FakeSubscriber());
        channel.subscribe(AUCTION, new FakeSubscriber());

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isEqualTo(2);
    }

    @Test
    @DisplayName("아무도 없는 방은 0명이다")
    void emptyRoomCountsZero() {
        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("경매방끼리 구독이 섞이지 않는다")
    void roomsAreIsolated() {
        channel.subscribe(AUCTION, new FakeSubscriber());
        channel.subscribe(AUCTION, new FakeSubscriber());

        int connected = channel.countSubscribers(OTHER_AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("해제한 구독은 접속자에서 빠진다")
    void unsubscribedSubscriberDropsOut() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(AUCTION, leaving);
        channel.subscribe(AUCTION, new FakeSubscriber());

        channel.unsubscribe(AUCTION, leaving);

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 구독을 두 번 등록해도 하나다")
    void subscribeIsIdempotent() {
        FakeSubscriber subscriber = new FakeSubscriber();

        channel.subscribe(AUCTION, subscriber);
        channel.subscribe(AUCTION, subscriber);

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 구독을 두 번 해제해도 결과가 같다")
    void unsubscribeIsIdempotent() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(AUCTION, leaving);

        channel.unsubscribe(AUCTION, leaving);
        channel.unsubscribe(AUCTION, leaving);

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("방의 모든 구독이 같은 현황을 받는다")
    void everySubscriberReceivesTheSameState() {
        FakeSubscriber first = new FakeSubscriber();
        FakeSubscriber second = new FakeSubscriber();
        channel.subscribe(AUCTION, first);
        channel.subscribe(AUCTION, second);

        RoomState state = liveState();
        channel.broadcast(AUCTION, state);

        assertThat(first.received).containsExactly(state);
        assertThat(second.received).containsExactly(state);
    }

    @Test
    @DisplayName("다른 방의 구독에는 가지 않는다")
    void broadcastDoesNotLeakToOtherRooms() {
        FakeSubscriber other = new FakeSubscriber();
        channel.subscribe(OTHER_AUCTION, other);
        channel.subscribe(AUCTION, new FakeSubscriber());

        channel.broadcast(AUCTION, liveState());

        assertThat(other.received).isEmpty();
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 걷어낸다")
    void closedSubscriberIsRemoved() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, new FakeSubscriber());

        channel.broadcast(AUCTION, liveState());

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("닫힌 구독이 있어도 나머지는 현황을 받는다")
    void openSubscriberReceivesDespiteClosedPeer() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        FakeSubscriber alive = new FakeSubscriber();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, alive);

        RoomState state = liveState();
        channel.broadcast(AUCTION, state);

        assertThat(alive.received).containsExactly(state);
    }

    @Test
    @DisplayName("모두 닫힌 방에 다시 보내도 터지지 않는다")
    void broadcastToDrainedRoomIsSafe() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);

        channel.broadcast(AUCTION, liveState());
        channel.broadcast(AUCTION, liveState());

        int connected = channel.countSubscribers(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("찔러 보기 전에는 살아 있어 보이던 구독도 걷어낸다")
    void sweepDetectsSilentlyClosedSubscriber() {
        FakeSubscriber silent = new FakeSubscriber();
        silent.closeOnPing();
        channel.subscribe(AUCTION, silent);
        channel.subscribe(AUCTION, new FakeSubscriber());

        int beforeSweep = channel.countSubscribers(AUCTION);

        Set<Long> swept = channel.sweepClosed();

        assertThat(beforeSweep).isEqualTo(2);
        assertThat(swept).containsExactly(AUCTION);
        assertThat(channel.countSubscribers(AUCTION)).isEqualTo(1);
    }

    @Test
    @DisplayName("모두 살아 있으면 걷어낸 방이 없다")
    void sweepReportsNothingWhenAllOpen() {
        channel.subscribe(AUCTION, new FakeSubscriber());

        Set<Long> swept = channel.sweepClosed();

        assertThat(swept).isEmpty();
    }

    @Test
    @DisplayName("방을 끊으면 그 방의 구독이 전부 끝나고 명부에서 빠진다")
    void closeRoomEndsEverySubscription() {
        FakeSubscriber first = new FakeSubscriber();
        FakeSubscriber second = new FakeSubscriber();
        channel.subscribe(AUCTION, first);
        channel.subscribe(AUCTION, second);

        channel.closeRoom(AUCTION);

        assertThat(first.closedByServer).isTrue();
        assertThat(second.closedByServer).isTrue();
        assertThat(channel.countSubscribers(AUCTION)).isZero();
    }

    @Test
    @DisplayName("다른 방의 구독은 끊지 않는다")
    void closeRoomLeavesOtherRooms() {
        FakeSubscriber other = new FakeSubscriber();
        channel.subscribe(OTHER_AUCTION, other);
        channel.subscribe(AUCTION, new FakeSubscriber());

        channel.closeRoom(AUCTION);

        assertThat(other.closedByServer).isFalse();
        assertThat(channel.countSubscribers(OTHER_AUCTION)).isEqualTo(1);
    }

    @Test
    @DisplayName("아무도 없는 방을 끊어도 터지지 않는다")
    void closingEmptyRoomIsSafe() {
        // 주기 작업이 방 목록을 받아 든 사이 마지막 사람이 나갈 수 있다
        channel.closeRoom(AUCTION);

        assertThat(channel.countSubscribers(AUCTION)).isZero();
    }

    @Test
    @DisplayName("해제는 실제로 뺐을 때만 뺐다고 답한다")
    void unsubscribeTellsWhetherItRemoved() {
        // 걷어내기가 먼저 빼 간 뒤에 해제 콜백이 돌아오므로, 호출자는 자기가 뺀 것인지 알아야 한다
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(AUCTION, leaving);

        boolean first = channel.unsubscribe(AUCTION, leaving);
        boolean second = channel.unsubscribe(AUCTION, leaving);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("걷어낸 구독의 해제가 뒤늦게 와도 뺐다고 답하지 않는다")
    void unsubscribeAfterDiscardTellsNothingWasRemoved() {
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, new FakeSubscriber());

        channel.broadcast(AUCTION, liveState());
        boolean removedByCallback = channel.unsubscribe(AUCTION, broken);

        assertThat(removedByCallback).isFalse();
    }

    @Test
    @DisplayName("전송에 실패해 걷어낸 구독은 연결도 끝낸다")
    void discardedSubscriberIsAlsoEnded() {
        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
        FakeSubscriber broken = new FakeSubscriber();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);

        channel.broadcast(AUCTION, liveState());

        assertThat(broken.closedByServer).isTrue();
    }

    @Test
    @DisplayName("찔러 보다 걷어낸 구독도 연결을 끝낸다")
    void sweptSubscriberIsAlsoEnded() {
        FakeSubscriber silent = new FakeSubscriber();
        silent.closeOnPing();
        channel.subscribe(AUCTION, silent);

        channel.sweepClosed();

        assertThat(silent.closedByServer).isTrue();
    }

    @Test
    @DisplayName("끊는 도중 되돌아온 해제 콜백에는 방이 이미 비어 있다")
    void roomIsAlreadyEmptyWhenReleaseCallbackReturns() {
        // 연결 하나가 끝나면 해제 콜백이 돌아와 남은 접속자 수를 다시 읽는다
        // 그때 방이 안 비어 있으면 한 명 끊을 때마다 조회와 방송이 한 번씩 돈다
        List<Integer> seenWhileCutting = new ArrayList<>();
        Runnable callback = () -> seenWhileCutting.add(channel.countSubscribers(AUCTION));
        channel.subscribe(AUCTION, new FakeSubscriber(callback));
        channel.subscribe(AUCTION, new FakeSubscriber(callback));

        channel.closeRoom(AUCTION);

        assertThat(seenWhileCutting).containsExactly(0, 0);
    }

    @Test
    @DisplayName("구독이 남은 방만 목록에 오른다")
    void subscribedAuctionsListsOnlyOccupiedRooms() {
        FakeSubscriber leaving = new FakeSubscriber();
        channel.subscribe(AUCTION, new FakeSubscriber());
        channel.subscribe(OTHER_AUCTION, leaving);
        channel.unsubscribe(OTHER_AUCTION, leaving);

        Set<Long> occupied = channel.subscribedAuctions();

        assertThat(occupied).containsExactly(AUCTION);
    }

    @Test
    @DisplayName("마지막 구독이 빠지는 순간 들어온 구독은 유실되지 않는다")
    void arrivingSubscriberSurvivesLastDeparture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int lost = 0;

        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                FakeSubscriber leaving = new FakeSubscriber();
                FakeSubscriber arriving = new FakeSubscriber();
                channel.subscribe(AUCTION, leaving);

                // 마지막 해제와 새 등록을 같은 순간에 풀어 준다
                CyclicBarrier gate = new CyclicBarrier(2);
                Future<?> departure = pool.submit(() -> {
                    await(gate);
                    channel.unsubscribe(AUCTION, leaving);
                });
                Future<?> arrival = pool.submit(() -> {
                    await(gate);
                    channel.subscribe(AUCTION, arriving);
                });
                departure.get();
                arrival.get();

                // 들어온 쪽은 남아 있어야 한다, 0이면 맵에서 떨어져 나간 집합에 들어간 것이다
                if (channel.countSubscribers(AUCTION) == 0) {
                    lost++;
                }
                channel.unsubscribe(AUCTION, arriving);
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

    // 채널은 현황을 나르기만 하고 안을 들여다보지 않으므로, 같은 객체가 갔는지만 확인하면 된다
    private static RoomState liveState() {
        return new RoomState(
                AUCTION, RoomPhase.LIVE, null, null, 0, 0,
                null, null, null, null, 0, new BidStats(0, 0), null, List.of());
    }

    // 닫힌 구독을 흉내내려면 열림 여부를 정할 수 있어야 한다
    private static final class FakeSubscriber implements RoomSubscriber {

        private final List<RoomState> received = new ArrayList<>();

        // 실제 연결은 끝나는 순간 해제 콜백이 되돌아온다, 그 되돌아옴을 이 자리에 심는다
        private final Runnable onClose;

        private boolean open = true;
        private boolean closeOnPing;
        private boolean closedByServer;

        FakeSubscriber() {
            this(() -> {
            });
        }

        FakeSubscriber(Runnable onClose) {
            this.onClose = onClose;
        }

        // 알리지 않고 사라진 상대, 서버가 끝낸 것과 구분한다
        void disconnect() {
            open = false;
        }

        // 실제 SSE 연결은 상대가 끊어도 써 보기 전까지는 살아 있는 것으로 보인다
        void closeOnPing() {
            closeOnPing = true;
        }

        @Override
        public void send(RoomState state) {
            if (!open) {
                return;
            }
            received.add(state);
        }

        @Override
        public void ping() {
            if (closeOnPing) {
                open = false;
            }
        }

        @Override
        public void close() {
            closedByServer = true;
            open = false;
            onClose.run();
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
