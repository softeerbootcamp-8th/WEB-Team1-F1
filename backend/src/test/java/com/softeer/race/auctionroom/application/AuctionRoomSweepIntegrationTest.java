package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomErrorCode;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// 브라우저가 조용히 끊긴 상황은 MockMvc 로 재현되지 않는다, 끊긴 구독을 흉내 낸 것을 채널에 직접 건다
// 구독은 우리가 관리하지 않는 바깥 연결이라 대역을 세워도 되고, 채널과 DB 는 실물 그대로 쓴다
@DisplayName("끊긴 구독 청소 통합 테스트")
class AuctionRoomSweepIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 방을 보는 사람, 낙찰자가 없는 방이라 누구로 보든 결과가 같다
    private static final long VIEWER_ID = 1L;

    @Autowired
    private AuctionRoomStreamService auctionRoomStreamService;

    @Autowired
    private AuctionRoomService auctionRoomService;

    @Autowired
    private RoomChannel roomChannel;

    private final FakeSubscriber alive = new FakeSubscriber();
    private final FakeSubscriber gone = new FakeSubscriber();

    // 채널은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다, 건 것을 모아 두었다가 끝나고 뺀다
    private final List<Subscription> subscriptions = new ArrayList<>();

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    @AfterEach
    void leaveRooms() {
        subscriptions.forEach(it -> roomChannel.unsubscribe(it.auctionId(), it.subscriber()));
    }

    private void subscribe(long auctionId, RoomSubscriber subscriber) {
        roomChannel.subscribe(auctionId, subscriber);
        subscriptions.add(new Subscription(auctionId, subscriber));
    }

    private record Subscription(long auctionId, RoomSubscriber subscriber) {
    }

    @Test
    @DisplayName("조용히 끊긴 구독을 걷어내면 남은 구독이 줄어든 접속자 수를 받는다")
    void sweepNotifiesRemainingSubscribers() {
        // given : 두 사람이 진행 중인 방에 있다가 한쪽이 알리지 않고 사라진다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);
        gone.disconnect();

        // when
        auctionRoomStreamService.sweepClosedSubscriptions();

        // then 1 : 남은 사람은 줄어든 접속자 수를 받는다, 다시 조회하지 않았는데 갱신된다
        assertThat(alive.lastState().connectedCount()).isEqualTo(1);
        assertThat(roomChannel.countSubscribers(auctionId)).isEqualTo(1);

        // then 2 : 사라진 쪽에는 아무것도 보내지 않는다
        assertThat(gone.received()).isEmpty();
    }

    @Test
    @DisplayName("아무도 끊기지 않았으면 현황을 새로 보내지 않는다")
    void sweepStaysQuietWhenAllOpen() {
        // given
        subscribe(liveRoom(), alive);

        // when
        auctionRoomStreamService.sweepClosedSubscriptions();

        // then : 살아 있는지 확인만 하고 끝난다, 5초마다 같은 현황을 다시 밀지 않는다
        assertThat(alive.received()).isEmpty();
    }

    @Test
    @DisplayName("방이 닫히면 남은 구독은 접속자에서 빠지고 새 조회는 거절된다")
    void closedRoomCountsNobodyAsConnected() {
        // given : 진행 중일 때 둘이 들어와 있다
        long auctionId = liveRoom();
        subscribe(auctionId, alive);
        subscribe(auctionId, gone);

        // when : 결과 구간까지 지나 방이 닫힌 뒤 한쪽이 조용히 사라져 갱신이 돈다
        fixClockAt(NOW.plusMinutes(30));
        gone.disconnect();
        auctionRoomStreamService.sweepClosedSubscriptions();

        // then 1 : 연결은 아직 열려 있지만 닫힌 방은 접속자로 세지 않는다
        assertThat(alive.lastState().connectedCount()).isZero();

        // then 2 : 같은 순간 새로 들어오려는 사람은 아예 막힌다, 열어 둔 화면과 새 조회가 어긋나지 않는다
        assertThat(catchThrowable(() -> auctionRoomService.enterRoom(auctionId, VIEWER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(AuctionRoomErrorCode.ROOM_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("한 방의 데이터 결함이 다른 방의 갱신을 멈추지 않는다")
    void brokenRoomDoesNotStopOtherRooms() {
        // given : 한 글자 실명 입찰자가 있는 방, 호가를 마스킹하는 순간 터진다
        long brokenRoom = liveRoomWithBidderNamed("김");
        FakeSubscriber brokenAlive = new FakeSubscriber();
        FakeSubscriber brokenGone = new FakeSubscriber();
        subscribe(brokenRoom, brokenAlive);
        subscribe(brokenRoom, brokenGone);

        // given : 아무 결함이 없는 방
        long healthyRoom = liveRoom();
        FakeSubscriber healthyAlive = new FakeSubscriber();
        FakeSubscriber healthyGone = new FakeSubscriber();
        subscribe(healthyRoom, healthyAlive);
        subscribe(healthyRoom, healthyGone);

        // when : 두 방에서 한 명씩 사라져 청소가 둘 다 갱신하려 한다
        brokenGone.disconnect();
        healthyGone.disconnect();
        Throwable thrown = catchThrowable(() -> auctionRoomStreamService.sweepClosedSubscriptions());

        // then 1 : 한 방이 터져도 청소가 통째로 멈추지 않는다
        assertThat(thrown).isNull();

        // then 2 : 멀쩡한 방은 처리 순서와 무관하게 줄어든 접속자 수를 받는다
        assertThat(healthyAlive.lastState().connectedCount()).isEqualTo(1);
    }

    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .create();
    }

    private long liveRoomWithBidderNamed(String realName) {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .bid(NOW.minusMinutes(1), users.user(realName, Role.DEALER), 12_500_000L)
                .create();
    }

    // 열려 있다가 알리지 않고 끊긴 연결, 청소가 찔러 봐야 드러난다
    private static class FakeSubscriber implements RoomSubscriber {

        private final List<RoomState> received = new ArrayList<>();
        private boolean open = true;

        void disconnect() {
            open = false;
        }

        List<RoomState> received() {
            return received;
        }

        RoomState lastState() {
            return received.getLast();
        }

        @Override
        public void send(RoomState state) {
            received.add(state);
        }

        @Override
        public void ping() {
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}