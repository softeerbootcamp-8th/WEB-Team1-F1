package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import com.softeer.race.auctionroom.domain.RoomPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 연결 하나가 구독 하나이고 그 수가 곧 접속자 수다, 채널은 현황 내용을 보지 않고 나르기만 한다
class InMemoryRoomChannelTest {

    private static final long AUCTION = 1L;
    private static final long OTHER_AUCTION = 2L;

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
        broken.close();
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
        broken.close();
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
        broken.close();
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

    // 채널은 현황을 나르기만 하고 안을 들여다보지 않으므로, 같은 객체가 갔는지만 확인하면 된다
    private static RoomState liveState() {
        return new RoomState(
                AUCTION, RoomPhase.LIVE, null, null, 0, 0,
                null, null, null, null, 0, 0, 0, null, List.of());
    }

    // 닫힌 구독을 흉내내려면 열림 여부를 정할 수 있어야 한다
    private static final class FakeSubscriber implements RoomSubscriber {

        private final List<RoomState> received = new ArrayList<>();
        private boolean open = true;
        private boolean closeOnPing;

        void close() {
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
        public boolean isOpen() {
            return open;
        }
    }
}
