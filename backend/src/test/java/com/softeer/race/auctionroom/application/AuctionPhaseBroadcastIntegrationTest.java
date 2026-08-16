package com.softeer.race.auctionroom.application;

import com.softeer.race.auction.application.AuctionCloser;
import com.softeer.race.auction.application.AuctionStarter;
import com.softeer.race.auctionroom.domain.RoomPhase;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

// 시간이 흘러 방의 단계가 바뀌는 경로만 본다, 입찰로 갱신되는 경로는 BidBroadcastIntegrationTest 가 본다
// 주기 작업은 테스트에서 꺼져 있으므로 전이를 일으키는 협력자를 스케줄러 대신 직접 부른다
// 구독은 우리가 관리하지 않는 바깥 연결이라 대역을 세우고, 채널과 DB 는 실물 그대로 쓴다
@DisplayName("경매 단계 전이 브로드캐스트 통합 테스트")
class AuctionPhaseBroadcastIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 방은 열려 있고 입찰만 아직 시작되지 않은 시각이다, 개장은 시작 30분 전이다
    private static final LocalDateTime START_AT = NOW.plusMinutes(10);

    // 이미 시작된 방의 시작 시각과 그 방의 마감, 경매는 시작 20분 뒤에 마감된다
    private static final LocalDateTime STARTED_AT = NOW.minusMinutes(15);
    private static final LocalDateTime END_AT = STARTED_AT.plusMinutes(20);

    @Autowired
    private AuctionStarter auctionStarter;

    @Autowired
    private AuctionCloser auctionCloser;

    @Autowired
    private RoomStreamService roomStreamService;

    @Autowired
    private RoomChannel roomChannel;

    private final RecordingSubscription watcher = new RecordingSubscription();

    private long auctionId;

    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
    }

    // 채널은 테이블이 아니라 컨텍스트에 남으므로 정리 훅이 지워 주지 않는다
    @AfterEach
    void leaveRoom() {
        roomChannel.unsubscribe(auctionId, watcher);
    }

    @Test
    @DisplayName("시나리오 1 : 시작 시각이 되면 대기 중이던 방에 진행중 현황이 나간다")
    void liveStateReachesWatchers() {
        // given : 시작을 기다리는 방을 한 사람이 보고 있다
        auctionId = waitingRoom();
        roomStreamService.subscribe(auctionId, watcher);

        // 구독만으로는 현황이 오지 않는다, 이 단정이 있어야 아래 현황이 전이에서 온 것임이 갈린다
        assertThat(watcher.received()).isEmpty();

        // when : 시작 시각이 되어 경매가 진행중으로 넘어간다
        fixClockAt(START_AT);
        auctionStarter.start(auctionId);

        // then : 다시 조회하지 않았는데 진행중으로 바뀐 현황이 흘러 들어간다
        assertThat(watcher.received()).hasSize(1);
        assertThat(watcher.lastState().phase()).isEqualTo(RoomPhase.LIVE);
    }

    @Test
    @DisplayName("시나리오 2 : 마감 시각이 지나 낙찰이 확정되면 결과 현황과 낙찰자가 나간다")
    void resultAndWinnerReachWatchers() {
        // given : 입찰이 한 건 들어온 진행중 방을 한 사람이 보고 있다
        auctionId = liveRoomWithBid();
        auctionInProgress();
        roomStreamService.subscribe(auctionId, watcher);

        // 구독만으로는 현황이 오지 않는다, 이 단정이 있어야 아래 현황이 마감에서 온 것임이 갈린다
        assertThat(watcher.received()).isEmpty();

        // when : 마감 시각이 지나 낙찰자가 확정된다
        fixClockAt(END_AT);
        auctionCloser.close(auctionId);

        // then 1 : 다시 조회하지 않았는데 결과 단계로 바뀐 현황이 흘러 들어간다
        assertThat(watcher.lastState().phase()).isEqualTo(RoomPhase.RESULT);

        // then 2 : 확정된 낙찰자가 마스킹된 이름으로 함께 실린다
        assertThat(watcher.lastState().winnerName().value()).isEqualTo("김*찰");
    }

    @Test
    @DisplayName("시나리오 3 : 입찰 없이 끝난 방에도 결과 현황이 나가고 낙찰자는 없다")
    void unsoldResultReachesWatchers() {
        // given : 입찰이 한 건도 없는 진행중 방을 한 사람이 보고 있다
        auctionId = liveRoom();
        auctionInProgress();
        roomStreamService.subscribe(auctionId, watcher);

        // when : 마감 시각이 지나 유찰로 끝난다
        fixClockAt(END_AT);
        auctionCloser.close(auctionId);

        // then 1 : 낙찰이 없어도 끝났다는 사실은 보고 있던 사람에게 닿는다
        assertThat(watcher.lastState().phase()).isEqualTo(RoomPhase.RESULT);

        // then 2 : 낙찰자는 없다, 화면은 이것으로 유찰을 안다
        assertThat(watcher.lastState().winnerName()).isNull();
    }

    @Test
    @DisplayName("시나리오 4 : 이미 시작된 경매에 시작 전이가 다시 들어와도 현황을 또 보내지 않는다")
    void alreadyStartedAuctionStaysQuiet() {
        // given : 시작 시각이 지나 한 번 진행중으로 넘어간 방을 한 사람이 보고 있다
        auctionId = waitingRoom();
        roomStreamService.subscribe(auctionId, watcher);
        fixClockAt(START_AT);
        auctionStarter.start(auctionId);
        int receivedAfterStart = watcher.received().size();

        // when : 같은 경매에 시작 전이가 한 번 더 들어온다, 서버가 여러 대면 같은 후보를 함께 뽑는다
        auctionStarter.start(auctionId);

        // then : 잠금 안에서 되돌아가므로 사건이 나가지 않는다, 화면이 같은 값을 두 번 받지 않는다
        assertThat(watcher.received()).hasSize(receivedAfterStart);
    }

    @Test
    @DisplayName("시나리오 5 : 이미 끝난 경매에 마감 확정이 다시 들어와도 현황을 또 보내지 않는다")
    void alreadyEndedAuctionStaysQuiet() {
        // given : 마감 시각이 지나 낙찰까지 확정된 방을 한 사람이 보고 있다
        auctionId = liveRoomWithBid();
        auctionInProgress();
        roomStreamService.subscribe(auctionId, watcher);
        fixClockAt(END_AT);
        auctionCloser.close(auctionId);
        int receivedAfterClose = watcher.received().size();

        // when : 같은 경매에 확정이 한 번 더 들어온다
        auctionCloser.close(auctionId);

        // then : 확정된 경매는 다시 확정되지 않아 사건도 나가지 않는다
        assertThat(watcher.received()).hasSize(receivedAfterClose);
    }

    @Test
    @DisplayName("시나리오 6 : 마감되면 결과 현황을 보내고 나서 연결을 끝낸다")
    void closingEndsConnectionAfterLastState() {
        // given : 입찰이 한 건 들어온 진행중 방을 한 사람이 보고 있다
        auctionId = liveRoomWithBid();
        auctionInProgress();
        roomStreamService.subscribe(auctionId, watcher);

        // when : 마감 시각이 지나 낙찰자가 확정된다
        fixClockAt(END_AT);
        auctionCloser.close(auctionId);

        // then 1 : 순서가 뒤집히면 화면은 결과를 못 받은 채 끊긴다, 마지막 현황이 먼저 닿아야 한다
        assertThat(watcher.receivedWhenClosed()).isEqualTo(watcher.received().size());
        assertThat(watcher.lastState().phase()).isEqualTo(RoomPhase.RESULT);

        // then 2 : 더 바뀔 값이 없는 구간이라 연결을 붙잡지 않는다, 주기 정리를 기다리지 않는다
        assertThat(roomChannel.viewerCount(auctionId)).isZero();
    }

    private long waitingRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), START_AT)
                .create();
    }

    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), STARTED_AT)
                .create();
    }

    private long liveRoomWithBid() {
        return rooms.room(users.user("박판매", Role.GENERAL), STARTED_AT)
                .bid(NOW.minusMinutes(10), users.user("김입찰", Role.DEALER), 12_500_000L)
                .create();
    }

    // 확정은 진행중인 경매만 받는다, 스케줄러가 밟는 순서를 그대로 밟아 상태를 올려 둔다
    private void auctionInProgress() {
        auctionStarter.start(auctionId);
    }

    private static final class RecordingSubscription implements RoomSubscription {

        // 이 테스트는 사람이 몇인지 보지 않는다, 서로 다른 사람이기만 하면 된다
        private static final AtomicLong VIEWER_SERIAL = new AtomicLong();

        private final List<RoomState> received = new ArrayList<>();
        private final long viewerId = VIEWER_SERIAL.incrementAndGet();

        // 끊김만 기록하면 마지막 현황보다 먼저 끊겼는지 알 수 없다, 끊긴 시점까지 받은 개수를 남긴다
        private int receivedWhenClosed = -1;

        @Override
        public long viewerId() {
            return viewerId;
        }

        List<RoomState> received() {
            return received;
        }

        RoomState lastState() {
            return received.getLast();
        }

        @Override
        public void send(RoomMessage message) {
            if (message instanceof RoomState state) {
                received.add(state);
            }
        }

        @Override
        public void ping() {
        }

        @Override
        public void close() {
            receivedWhenClosed = received.size();
        }

        @Override
        public boolean isOpen() {
            return receivedWhenClosed < 0;
        }

        int receivedWhenClosed() {
            return receivedWhenClosed;
        }
    }
}