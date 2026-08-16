package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RoomErrorCode;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// 구독을 정리하는 주기 작업 둘을 함께 본다, 조용히 끊긴 것을 걷어내는 쪽과 끝난 방을 서버가 끊는 쪽이다
// 브라우저가 조용히 끊긴 상황은 MockMvc 로 재현되지 않으므로 끊긴 구독을 흉내 낸 것을 채널에 직접 건다
// 구독은 우리가 관리하지 않는 바깥 연결이라 대역을 세워도 되고, 채널과 DB 는 실물 그대로 쓴다
@DisplayName("경매방 구독 정리 통합 테스트")
class RoomCleanupIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 방을 보는 사람, 낙찰자가 없는 방이라 누구로 보든 결과가 같다
    private static final long VIEWER_ID = 1L;

    @Autowired
    private RoomStreamService roomStreamService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomChannel roomChannel;

    private final FakeSubscription alive = new FakeSubscription();
    private final FakeSubscription gone = new FakeSubscription();

    // 채널은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다, 건 것을 모아 두었다가 끝나고 뺀다
    private final List<Subscription> subscriptions = new ArrayList<>();

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @AfterEach
    void leaveRooms() {
        subscriptions.forEach(it -> roomChannel.unsubscribe(it.auctionId(), it.subscription()));
    }

    private void subscribe(long auctionId, RoomSubscription subscription) {
        roomChannel.subscribe(auctionId, subscription);
        subscriptions.add(new Subscription(auctionId, subscription));
    }

    private record Subscription(long auctionId, RoomSubscription subscription) {
    }

    @Test
    @DisplayName("시나리오 1 : 조용히 끊긴 구독을 걷어내면 남은 구독이 줄어든 접속자 수를 받는다")
    void sweepNotifiesRemainingSubscribers() {
        // given : 두 사람이 진행 중인 방에 있다가 한쪽이 알리지 않고 사라진다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);
        gone.disconnect();

        // when
        roomStreamService.sweepClosedSubscriptions();

        // then 1 : 남은 사람은 줄어든 접속자 수를 받는다, 다시 조회하지 않았는데 갱신된다
        assertThat(alive.lastViewerCount()).isEqualTo(1);
        assertThat(roomChannel.viewerCount(auctionId)).isEqualTo(1);

        // then 2 : 사라진 쪽에는 아무것도 보내지 않는다
        assertThat(gone.received()).isEmpty();
    }

    @Test
    @DisplayName("시나리오 2 : 아무도 끊기지 않았으면 현황을 새로 보내지 않는다")
    void sweepStaysQuietWhenAllOpen() {
        // given
        subscribe(liveRoom(), alive);

        // when
        roomStreamService.sweepClosedSubscriptions();

        // then : 살아 있는지 확인만 하고 끝난다, 주기가 돌 때마다 같은 현황을 다시 밀지 않는다
        // 이 단정이 걷어내기 주기를 짧게 잡을 수 있는 근거다, 방송이 안 나갔다는 것은 DB 도 안 읽었다는 뜻이다
        assertThat(alive.received()).isEmpty();
    }

    @Test
    @DisplayName("시나리오 3 : 청소가 닫힌 방을 지나가면 남은 연결도 끝나고 새 조회는 거절된다")
    void sweepOnClosedRoomEndsConnections() {
        // given : 진행 중일 때 둘이 들어와 있다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);

        // when : 결과 구간까지 지나 방이 닫힌 뒤 한쪽이 조용히 사라져 갱신이 돈다
        fixClockAt(NOW.plusMinutes(30));
        gone.disconnect();
        roomStreamService.sweepClosedSubscriptions();

        // then 1 : 마지막 현황이 나간 뒤 남아 있던 연결도 끝난다, 끊기 주기를 기다리지 않는다
        assertThat(alive.closedByServer).isTrue();

        // then 2 : 같은 순간 새로 들어오려는 사람은 아예 막힌다, 열어 둔 화면과 새 조회가 어긋나지 않는다
        assertThat(catchThrowable(() -> roomService.readRoom(auctionId, VIEWER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(RoomErrorCode.ROOM_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("시나리오 4 : 한 방의 데이터 결함이 다른 방의 갱신을 멈추지 않는다")
    void brokenRoomDoesNotStopOtherRooms() {
        // given : 실명이 한 글자로 망가진 입찰자가 있는 방, 호가를 마스킹하는 순간 터진다
        long brokenRoom = liveRoomWithOneLetterBidder();
        FakeSubscription brokenAlive = new FakeSubscription();
        FakeSubscription brokenGone = new FakeSubscription();
        subscribe(brokenRoom, brokenAlive);
        subscribe(brokenRoom, brokenGone);

        // given : 아무 결함이 없는 방
        long healthyRoom = liveRoom();
        FakeSubscription healthyAlive = new FakeSubscription();
        FakeSubscription healthyGone = new FakeSubscription();
        subscribe(healthyRoom, healthyAlive);
        subscribe(healthyRoom, healthyGone);

        // when : 두 방에서 한 명씩 사라져 청소가 둘 다 갱신하려 한다
        brokenGone.disconnect();
        healthyGone.disconnect();
        Throwable thrown = catchThrowable(() -> roomStreamService.sweepClosedSubscriptions());

        // then 1 : 한 방이 터져도 청소가 통째로 멈추지 않는다
        assertThat(thrown).isNull();

        // then 2 : 멀쩡한 방은 처리 순서와 무관하게 줄어든 접속자 수를 받는다
        assertThat(healthyAlive.lastViewerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 5 : 걷어낸 뒤 뒤늦게 온 해제는 현황을 다시 보내지 않는다")
    void lateReleaseAfterSweepDoesNotRefreshAgain() {
        // given : 한 명이 조용히 사라져 청소가 걷어냈고, 남은 사람은 줄어든 수를 이미 받았다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);
        gone.disconnect();
        roomStreamService.sweepClosedSubscriptions();

        int receivedAfterSweep = alive.received().size();

        // when : 끝난 연결의 해제 콜백이 뒤늦게 돌아온다, 실제 컨테이너에서 나는 그 순서다
        roomStreamService.unsubscribe(auctionId, gone);

        // then : 이미 빠진 구독이라 같은 방을 다시 읽지도 보내지도 않는다
        assertThat(alive.received()).hasSize(receivedAfterSweep);
    }

    @Test
    @DisplayName("시나리오 6 : 결과 구간까지 지난 방은 남은 연결을 서버가 끊는다")
    void closedRoomConnectionsAreCutByServer() {
        // given : 진행 중일 때 둘이 들어와 있다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);

        // when : 마감 후 결과 구간까지 지나 방이 닫힌다
        fixClockAt(NOW.plusMinutes(30));
        roomStreamService.closeStreamEndedRooms();

        // then 1 : 둘 다 서버가 끝냈고 명부에도 남지 않는다
        assertThat(alive.closedByServer).isTrue();
        assertThat(gone.closedByServer).isTrue();
        assertThat(roomChannel.viewerCount(auctionId)).isZero();

        // then 2 : 끊는 동안 현황을 다시 보내지 않는다, 명부를 먼저 비우므로 갱신할 방이 없다
        assertThat(alive.received()).isEmpty();
    }

    @Test
    @DisplayName("시나리오 7 : 아직 열려 있는 방의 연결은 끊지 않는다")
    void openRoomKeepsConnections() {
        // given : 진행 중인 방에 한 사람이 있다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);

        // when : 같은 주기 작업이 돈다
        roomStreamService.closeStreamEndedRooms();

        // then : 볼 것이 남은 방이라 그대로 둔다
        assertThat(alive.closedByServer).isFalse();
        assertThat(roomChannel.viewerCount(auctionId)).isEqualTo(1);
    }

    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .create();
    }

    // User.create 가 두 글자 미만을 막으므로 정상으로 만든 뒤 DB 를 직접 고친다
    // 이 결함이 남은 경로가 그것뿐이라, 테스트가 실제로 가능한 상황을 그대로 재현한다
    private long liveRoomWithOneLetterBidder() {
        User bidder = users.user("김입찰", Role.DEALER);

        long auctionId = rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .bid(NOW.minusMinutes(1), bidder, 12_500_000L)
                .create();

        jdbcTemplate.update("update users set real_name = ? where id = ?", "김", bidder.getId());

        return auctionId;
    }

    // 열려 있다가 알리지 않고 끊긴 연결, 청소가 찔러 봐야 드러난다
    private static class FakeSubscription implements RoomSubscription {

        // 이 테스트는 사람이 몇인지 보지 않는다, 서로 다른 사람이기만 하면 된다
        private static final AtomicLong VIEWER_SERIAL = new AtomicLong();

        private final List<RoomState> received = new ArrayList<>();
        private final List<ViewerCount> viewerCounts = new ArrayList<>();
        private final long viewerId = VIEWER_SERIAL.incrementAndGet();
        private boolean open = true;
        private boolean closedByServer;

        @Override
        public long viewerId() {
            return viewerId;
        }

        void disconnect() {
            open = false;
        }

        List<RoomState> received() {
            return received;
        }

        RoomState lastState() {
            return received.getLast();
        }

        int lastViewerCount() {
            return viewerCounts.getLast().viewerCount();
        }

        @Override
        public void send(RoomMessage message) {
            switch (message) {
                case RoomState state -> received.add(state);
                case ViewerCount viewers -> viewerCounts.add(viewers);
            }
        }

        @Override
        public void ping() {
        }

        @Override
        public void close() {
            closedByServer = true;
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}