package com.softeer.race.auctionroom.application;

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

// 브라우저가 조용히 끊긴 상황은 MockMvc 로 재현되지 않는다, 끊긴 구독을 흉내 낸 것을 채널에 직접 건다
// 구독은 우리가 관리하지 않는 바깥 연결이라 대역을 세워도 되고, 채널과 DB 는 실물 그대로 쓴다
@DisplayName("끊긴 구독 청소 통합 테스트")
class AuctionRoomSweepIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    @Autowired
    private AuctionRoomStreamService auctionRoomStreamService;

    @Autowired
    private RoomChannel roomChannel;

    private final FakeSubscriber alive = new FakeSubscriber();
    private final FakeSubscriber gone = new FakeSubscriber();

    private long auctionId;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    // 채널은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다
    @AfterEach
    void leaveRoom() {
        roomChannel.unsubscribe(auctionId, alive);
        roomChannel.unsubscribe(auctionId, gone);
    }

    @Test
    @DisplayName("조용히 끊긴 구독을 걷어내면 남은 구독이 줄어든 접속자 수를 받는다")
    void sweepNotifiesRemainingSubscribers() {
        // given : 두 사람이 진행 중인 방에 있다가 한쪽이 알리지 않고 사라진다
        auctionId = liveRoom();
        roomChannel.subscribe(auctionId, alive);
        roomChannel.subscribe(auctionId, gone);
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
        auctionId = liveRoom();
        roomChannel.subscribe(auctionId, alive);

        // when
        auctionRoomStreamService.sweepClosedSubscriptions();

        // then : 살아 있는지 확인만 하고 끝난다, 5초마다 같은 현황을 다시 밀지 않는다
        assertThat(alive.received()).isEmpty();
    }

    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
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