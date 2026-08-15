package com.softeer.race.auctionroom.application;

import com.softeer.race.bid.application.BidService;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

// 입찰이 방을 갱신하는 경로만 본다, 입찰 자체의 계약은 BidIntegrationTest 가 컨트롤러부터 관통해 확인한다
@DisplayName("입찰 브로드캐스트 통합 테스트")
@Sql("/sql/bid-increment-bands.sql")
class BidBroadcastIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 45, 12);

    // 시더 기본 시작가와 이 가격대의 상승가, 첫 입찰도 시작가 + 상승가 배수면 성립한다
    private static final long START_PRICE = 10_000_000L;
    private static final long INCREMENT = 50_000L;
    private static final long BID_AMOUNT = START_PRICE + INCREMENT;

    @Autowired
    private BidService bidService;

    @Autowired
    private RoomStreamService roomStreamService;

    @Autowired
    private RoomChannel roomChannel;

    private final RecordingSubscriber watcher = new RecordingSubscriber();

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
    @DisplayName("시나리오 1 : 입찰이 성립하면 보고 있던 사람의 현황이 갱신된다")
    void bidReachesWatchers() {
        // given : 진행 중인 방을 한 사람이 보고 있다, 들어올 때 받은 첫 현황은 아직 시작가다
        auctionId = liveRoom();
        roomStreamService.subscribe(auctionId, watcher);
        assertThat(watcher.lastState().currentPrice()).isEqualTo(START_PRICE);

        // when : 다른 사람이 입찰한다
        User bidder = users.user("김입찰", Role.DEALER);
        bidService.place(auctionId, bidder.getId(), BID_AMOUNT);

        // then : 다시 조회하지 않았는데 갱신된 현황이 흘러 들어간다
        RoomState last = watcher.lastState();

        assertThat(last.currentPrice()).isEqualTo(BID_AMOUNT);
        assertThat(last.bidCounts().bidCount()).isEqualTo(1);
        assertThat(last.bidCounts().bidderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2 : 마감 직전 입찰로 연장되면 밀린 마감도 함께 나간다")
    void extendedDeadlineReachesWatchers() {
        // given : 마감이 10초 남은 방을 한 사람이 보고 있다
        auctionId = closingRoom();
        roomStreamService.subscribe(auctionId, watcher);
        assertThat(watcher.lastState().endAt()).isEqualTo(NOW.plusSeconds(10));

        // when : 소프트 클로즈 임계 안에서 입찰이 들어온다
        User bidder = users.user("김입찰", Role.DEALER);
        bidService.place(auctionId, bidder.getId(), BID_AMOUNT);

        // then : 밀린 마감이 현황에 실려 나간다, 다시 조회하지 않았는데 화면이 안다
        assertThat(watcher.lastState().endAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    @Transactional
    @DisplayName("시나리오 3 : 입찰이 롤백되면 방송도 나가지 않는다")
    void rolledBackBidIsNotBroadcast() {
        // given : 진행 중인 방을 한 사람이 보고 있다
        auctionId = liveRoom();
        roomStreamService.subscribe(auctionId, watcher);
        int beforeBid = watcher.received().size();

        // when : 이 메서드의 트랜잭션 안에서 입찰한다, 테스트가 끝나며 롤백된다
        User bidder = users.user("김입찰", Role.DEALER);
        bidService.place(auctionId, bidder.getId(), BID_AMOUNT);

        // then : 없던 일이 될 입찰은 방에 나가지 않아야 한다
        assertThat(watcher.received()).hasSize(beforeBid);
    }

    // 마감 30초 임계 밖이라 이 방의 입찰은 마감을 밀지 않는다
    private long liveRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(15))
                .create();
    }

    // 마감이 10초 남아 소프트 클로즈 임계(30초) 안이다, 입찰이 마감을 NOW + 30초로 민다
    private long closingRoom() {
        return rooms.room(users.user("박판매", Role.GENERAL), NOW.minusMinutes(20).plusSeconds(10))
                .create();
    }

    // 받은 현황만 기록하면 되는 구독, 끊김은 이 테스트가 보지 않는다
    private static final class RecordingSubscriber implements RoomSubscriber {

        // 이 테스트는 사람이 몇인지 보지 않는다, 서로 다른 사람이기만 하면 된다
        private static final AtomicLong VIEWER_SERIAL = new AtomicLong();

        private final List<RoomState> received = new ArrayList<>();
        private final long viewerId = VIEWER_SERIAL.incrementAndGet();

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
        public void send(RoomState state) {
            received.add(state);
        }

        @Override
        public void ping() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
