package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomMessage;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.domain.RoomPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// 구독은 창 하나에 하나지만 한 사람이 창을 여럿 연다, 세는 단위는 창이 아니라 사람이다
// 채널은 현황 내용을 보지 않고 나르기만 한다
class InMemoryRoomChannelTest {

    private static final long AUCTION = 1L;
    private static final long OTHER_AUCTION = 2L;

    private static final long VIEWER = 7L;

    // 창이 좁아 한 번으로는 못 잡는다, 반복 횟수가 곧 검출력이다
    private static final int RACE_ROUNDS = 10_000;

    private final InMemoryRoomChannel channel = new InMemoryRoomChannel();

    @Test
    @DisplayName("서로 다른 사람의 창은 각각 센다")
    void differentPeopleAreCountedSeparately() {
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.subscribe(AUCTION, new FakeSubscription());

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isEqualTo(2);
    }

    @Test
    @DisplayName("한 사람이 창을 둘 열어도 한 명이다")
    void oneViewerWithTwoWindowsCountsOnce() {
        channel.subscribe(AUCTION, new FakeSubscription(VIEWER));
        channel.subscribe(AUCTION, new FakeSubscription(VIEWER));

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("사람은 마지막 창이 닫힐 때 빠진다")
    void viewerLeavesOnlyWhenLastWindowCloses() {
        // 창이 셋이어야 한다, 둘이면 하나를 닫았을 때 남은 구독 수와 사람 수가 똑같이 1 이라
        // 구독을 세는 구현과 사람을 세는 구현이 같은 답을 내고 이 테스트가 둘을 못 가른다
        FakeSubscription first = new FakeSubscription(VIEWER);
        FakeSubscription second = new FakeSubscription(VIEWER);
        FakeSubscription third = new FakeSubscription(VIEWER);
        channel.subscribe(AUCTION, first);
        channel.subscribe(AUCTION, second);
        channel.subscribe(AUCTION, third);

        channel.unsubscribe(AUCTION, first);
        int afterOneWindow = channel.viewerCount(AUCTION);

        channel.unsubscribe(AUCTION, second);
        channel.unsubscribe(AUCTION, third);

        assertThat(afterOneWindow).isEqualTo(1);
        assertThat(channel.viewerCount(AUCTION)).isZero();
    }

    @Test
    @DisplayName("방마다 사람 수가 담긴다")
    void viewerCountsAreGroupedByRoom() {
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.subscribe(OTHER_AUCTION, new FakeSubscription());

        assertThat(channel.viewerCountByRoom()).containsOnly(entry(AUCTION, 2), entry(OTHER_AUCTION, 1));
    }

    @Test
    @DisplayName("구독이 없는 방은 담기지 않는다")
    void emptyRoomIsAbsentFromViewerCounts() {
        FakeSubscription leaving = new FakeSubscription();
        channel.subscribe(AUCTION, leaving);

        channel.unsubscribe(AUCTION, leaving);

        assertThat(channel.viewerCountByRoom()).isEmpty();
    }

    @Test
    @DisplayName("한 사람이 창을 여럿 열어도 방별 집계에서 하나다")
    void oneViewerWithTwoWindowsCountsOnceInViewerCounts() {
        channel.subscribe(AUCTION, new FakeSubscription(VIEWER));
        channel.subscribe(AUCTION, new FakeSubscription(VIEWER));

        // 방별 집계를 구독 수로 짜면 여기서만 깨진다, 사람 단위 집계를 실제로 나눠 쓰는지 본다
        assertThat(channel.viewerCountByRoom()).containsExactly(entry(AUCTION, 1));
    }

    @Test
    @DisplayName("아무도 없는 방은 0명이다")
    void emptyRoomCountsZero() {
        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("경매방끼리 구독이 섞이지 않는다")
    void roomsAreIsolated() {
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.subscribe(AUCTION, new FakeSubscription());

        int connected = channel.viewerCount(OTHER_AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("해제한 구독은 접속자에서 빠진다")
    void unsubscribedSubscriberDropsOut() {
        FakeSubscription leaving = new FakeSubscription();
        channel.subscribe(AUCTION, leaving);
        channel.subscribe(AUCTION, new FakeSubscription());

        channel.unsubscribe(AUCTION, leaving);

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 구독을 두 번 등록해도 하나다")
    void subscribeIsIdempotent() {
        FakeSubscription subscription = new FakeSubscription();

        channel.subscribe(AUCTION, subscription);
        channel.subscribe(AUCTION, subscription);

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 구독을 두 번 해제해도 결과가 같다")
    void unsubscribeIsIdempotent() {
        FakeSubscription leaving = new FakeSubscription();
        channel.subscribe(AUCTION, leaving);

        channel.unsubscribe(AUCTION, leaving);
        channel.unsubscribe(AUCTION, leaving);

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("방의 모든 구독이 같은 현황을 받는다")
    void everySubscriberReceivesTheSameState() {
        FakeSubscription first = new FakeSubscription();
        FakeSubscription second = new FakeSubscription();
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
        FakeSubscription other = new FakeSubscription();
        channel.subscribe(OTHER_AUCTION, other);
        channel.subscribe(AUCTION, new FakeSubscription());

        channel.broadcast(AUCTION, liveState());

        assertThat(other.received).isEmpty();
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 걷어낸다")
    void closedSubscriberIsRemoved() {
        FakeSubscription broken = new FakeSubscription();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, new FakeSubscription());

        channel.broadcast(AUCTION, liveState());

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("닫힌 구독이 있어도 나머지는 현황을 받는다")
    void openSubscriberReceivesDespiteClosedPeer() {
        FakeSubscription broken = new FakeSubscription();
        broken.disconnect();
        FakeSubscription alive = new FakeSubscription();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, alive);

        RoomState state = liveState();
        channel.broadcast(AUCTION, state);

        assertThat(alive.received).containsExactly(state);
    }

    @Test
    @DisplayName("모두 닫힌 방에 다시 보내도 터지지 않는다")
    void broadcastToDrainedRoomIsSafe() {
        FakeSubscription broken = new FakeSubscription();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);

        channel.broadcast(AUCTION, liveState());
        channel.broadcast(AUCTION, liveState());

        int connected = channel.viewerCount(AUCTION);

        assertThat(connected).isZero();
    }

    @Test
    @DisplayName("찔러 보기 전에는 살아 있어 보이던 구독도 걷어낸다")
    void sweepDetectsSilentlyClosedSubscriber() {
        FakeSubscription silent = new FakeSubscription();
        silent.closeOnPing();
        channel.subscribe(AUCTION, silent);
        channel.subscribe(AUCTION, new FakeSubscription());

        int beforeSweep = channel.viewerCount(AUCTION);

        Set<Long> swept = channel.sweepClosed();

        assertThat(beforeSweep).isEqualTo(2);
        assertThat(swept).containsExactly(AUCTION);
        assertThat(channel.viewerCount(AUCTION)).isEqualTo(1);
    }

    @Test
    @DisplayName("모두 살아 있으면 걷어낸 방이 없다")
    void sweepReportsNothingWhenAllOpen() {
        channel.subscribe(AUCTION, new FakeSubscription());

        Set<Long> swept = channel.sweepClosed();

        assertThat(swept).isEmpty();
    }

    @Test
    @DisplayName("방을 끊으면 그 방의 구독이 전부 끝나고 명부에서 빠진다")
    void closeRoomEndsEverySubscription() {
        FakeSubscription first = new FakeSubscription();
        FakeSubscription second = new FakeSubscription();
        channel.subscribe(AUCTION, first);
        channel.subscribe(AUCTION, second);

        channel.closeRoom(AUCTION);

        assertThat(first.closedByServer).isTrue();
        assertThat(second.closedByServer).isTrue();
        assertThat(channel.viewerCount(AUCTION)).isZero();
    }

    @Test
    @DisplayName("전부 끊으면 열려 있던 방들의 구독이 모두 끝난다")
    void closeAllEndsEveryRoom() {
        FakeSubscription here = new FakeSubscription();
        FakeSubscription there = new FakeSubscription();
        channel.subscribe(AUCTION, here);
        channel.subscribe(OTHER_AUCTION, there);

        channel.closeAll();

        assertThat(here.closedByServer).isTrue();
        assertThat(there.closedByServer).isTrue();
        assertThat(channel.subscribedRooms()).isEmpty();
    }

    @Test
    @DisplayName("다른 방의 구독은 끊지 않는다")
    void closeRoomLeavesOtherRooms() {
        FakeSubscription other = new FakeSubscription();
        channel.subscribe(OTHER_AUCTION, other);
        channel.subscribe(AUCTION, new FakeSubscription());

        channel.closeRoom(AUCTION);

        assertThat(other.closedByServer).isFalse();
        assertThat(channel.viewerCount(OTHER_AUCTION)).isEqualTo(1);
    }

    @Test
    @DisplayName("아무도 없는 방을 끊어도 터지지 않는다")
    void closingEmptyRoomIsSafe() {
        // 주기 작업이 방 목록을 받아 든 사이 마지막 사람이 나갈 수 있다
        channel.closeRoom(AUCTION);

        assertThat(channel.viewerCount(AUCTION)).isZero();
    }

    @Test
    @DisplayName("해제는 실제로 뺐을 때만 뺐다고 답한다")
    void unsubscribeTellsWhetherItRemoved() {
        // 걷어내기가 먼저 빼 간 뒤에 해제 콜백이 돌아오므로, 호출자는 자기가 뺀 것인지 알아야 한다
        FakeSubscription leaving = new FakeSubscription();
        channel.subscribe(AUCTION, leaving);

        boolean first = channel.unsubscribe(AUCTION, leaving);
        boolean second = channel.unsubscribe(AUCTION, leaving);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("걷어낸 구독의 해제가 뒤늦게 와도 뺐다고 답하지 않는다")
    void unsubscribeAfterDiscardTellsNothingWasRemoved() {
        FakeSubscription broken = new FakeSubscription();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);
        channel.subscribe(AUCTION, new FakeSubscription());

        channel.broadcast(AUCTION, liveState());
        boolean removedByCallback = channel.unsubscribe(AUCTION, broken);

        assertThat(removedByCallback).isFalse();
    }

    @Test
    @DisplayName("전송에 실패해 걷어낸 구독은 연결도 끝낸다")
    void discardedSubscriberIsAlsoEnded() {
        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
        FakeSubscription broken = new FakeSubscription();
        broken.disconnect();
        channel.subscribe(AUCTION, broken);

        channel.broadcast(AUCTION, liveState());

        assertThat(broken.closedByServer).isTrue();
    }

    @Test
    @DisplayName("찔러 보다 걷어낸 구독도 연결을 끝낸다")
    void sweptSubscriberIsAlsoEnded() {
        FakeSubscription silent = new FakeSubscription();
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
        Runnable callback = () -> seenWhileCutting.add(channel.viewerCount(AUCTION));
        channel.subscribe(AUCTION, new FakeSubscription(callback));
        channel.subscribe(AUCTION, new FakeSubscription(callback));

        channel.closeRoom(AUCTION);

        assertThat(seenWhileCutting).containsExactly(0, 0);
    }

    @Test
    @DisplayName("구독이 남은 방만 목록에 오른다")
    void subscribedRoomsListsOnlyOccupiedRooms() {
        FakeSubscription leaving = new FakeSubscription();
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.subscribe(OTHER_AUCTION, leaving);
        channel.unsubscribe(OTHER_AUCTION, leaving);

        Set<Long> occupied = channel.subscribedRooms();

        assertThat(occupied).containsExactly(AUCTION);
    }

    @Test
    @DisplayName("마지막 구독이 빠지는 순간 들어온 구독은 유실되지 않는다")
    void arrivingSubscriberSurvivesLastDeparture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int lost = 0;

        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                FakeSubscription leaving = new FakeSubscription();
                FakeSubscription arriving = new FakeSubscription();
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
                if (channel.viewerCount(AUCTION) == 0) {
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

    @Test
    @DisplayName("방송 뒤에 붙은 구독이 마지막 현황을 따라잡는다")
    void lateSubscriberCatchesUpToLastState() {
        channel.subscribe(AUCTION, new FakeSubscription());
        RoomState state = liveState();
        channel.broadcast(AUCTION, state);

        FakeSubscription late = new FakeSubscription();
        channel.subscribe(AUCTION, late);
        channel.catchUp(AUCTION, late);

        assertThat(late.received).containsExactly(state);
    }

    @Test
    @DisplayName("나간 현황이 없으면 따라잡을 것도 없다")
    void catchUpSendsNothingWithoutBroadcast() {
        FakeSubscription late = new FakeSubscription();
        channel.subscribe(AUCTION, late);

        channel.catchUp(AUCTION, late);

        assertThat(late.received).isEmpty();
    }

    @Test
    @DisplayName("낡은 현황이 늦게 도착해도 기억하는 것은 최신이다")
    void catchUpKeepsTheNewestState() {
        channel.subscribe(AUCTION, new FakeSubscription());
        RoomState newer = liveState(2_000L);
        channel.broadcast(AUCTION, newer);
        channel.broadcast(AUCTION, liveState(1_000L));

        FakeSubscription late = new FakeSubscription();
        channel.subscribe(AUCTION, late);
        channel.catchUp(AUCTION, late);

        assertThat(late.received).containsExactly(newer);
    }

    @Test
    @DisplayName("방이 닫히면 기억한 현황도 지워진다")
    void closingRoomForgetsLastState() {
        channel.subscribe(AUCTION, new FakeSubscription());
        channel.broadcast(AUCTION, liveState());
        channel.closeRoom(AUCTION);

        FakeSubscription late = new FakeSubscription();
        channel.subscribe(AUCTION, late);
        channel.catchUp(AUCTION, late);

        assertThat(late.received).isEmpty();
    }

    @Test
    @DisplayName("마지막 구독이 빠지면 기억한 현황도 지워진다")
    void emptyingRoomForgetsLastState() {
        FakeSubscription only = new FakeSubscription();
        channel.subscribe(AUCTION, only);
        channel.broadcast(AUCTION, liveState());
        channel.unsubscribe(AUCTION, only);

        FakeSubscription late = new FakeSubscription();
        channel.subscribe(AUCTION, late);
        channel.catchUp(AUCTION, late);

        assertThat(late.received).isEmpty();
    }

    // 채널은 현황을 나르기만 하고 안을 들여다보지 않으므로, 같은 객체가 갔는지만 확인하면 된다
    private static RoomState liveState() {
        return liveState(0);
    }

    // 낡음을 가리는 것이 현재가이므로 그 값을 정할 수 있어야 한다
    private static RoomState liveState(long currentPrice) {
        return new RoomState(
                AUCTION, RoomPhase.LIVE, currentPrice,
                null, null, new BidCounts(0, 0), null, List.of());
    }

    // 닫힌 구독을 흉내내려면 열림 여부를 정할 수 있어야 한다
    private static final class FakeSubscription implements RoomSubscription {

        // 사람을 안 정하면 매번 다른 사람이다, 손으로 지정한 사람과 겹치지 않게 큰 값에서 시작한다
        private static final AtomicLong VIEWER_SERIAL = new AtomicLong(1_000L);

        private final List<RoomState> received = new ArrayList<>();

        private final long viewerId;

        // 실제 연결은 끝나는 순간 해제 콜백이 되돌아온다, 그 되돌아옴을 이 자리에 심는다
        private final Runnable onClose;

        private boolean open = true;
        private boolean closeOnPing;
        private boolean closedByServer;

        FakeSubscription() {
            this(VIEWER_SERIAL.incrementAndGet(), () -> {
            });
        }

        FakeSubscription(long viewerId) {
            this(viewerId, () -> {
            });
        }

        FakeSubscription(Runnable onClose) {
            this(VIEWER_SERIAL.incrementAndGet(), onClose);
        }

        private FakeSubscription(long viewerId, Runnable onClose) {
            this.viewerId = viewerId;
            this.onClose = onClose;
        }

        @Override
        public long viewerId() {
            return viewerId;
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
        public void send(RoomMessage message) {
            // 진짜 구독은 널을 받으면 사서함에서 터진다, 페이크가 더 관대하면 널을 보내는 실수를 여기서 못 잡는다
            Objects.requireNonNull(message, "보낼 것이 없으면 send 를 부르지 않는다");

            if (!open) {
                return;
            }
            if (message instanceof RoomState state) {
                received.add(state);
            }
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
