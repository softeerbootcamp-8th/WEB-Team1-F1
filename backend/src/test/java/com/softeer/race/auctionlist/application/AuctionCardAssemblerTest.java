package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import com.softeer.race.auctionroom.domain.RoomPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 조회와 방송이 같은 카드를 내야 하므로 조립 규칙을 여기서 못 박는다
class AuctionCardAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);
    private static final int WATCHING = 4;

    private final AuctionCardAssembler assembler = new AuctionCardAssembler(new AlwaysWatchedRooms());

    @Test
    @DisplayName("열린 단계는 경매방의 접속자 수를 그대로 쓴다")
    void openPhaseUsesRoomCount() {
        AuctionListRow live = row(1L, NOW.minusMinutes(10), NOW.plusMinutes(10));

        AuctionCardInfo card = assembler.assemble(live, NOW, Map.of());

        assertThat(card.phase()).isEqualTo(RoomPhase.LIVE);
        assertThat(card.connectedCount()).isEqualTo(WATCHING);
    }

    @Test
    @DisplayName("닫힌 단계는 방에 사람이 남아 있어도 0이다")
    void closedPhaseCountsNobody() {
        AuctionListRow closed = row(2L, NOW.minusHours(2), NOW.minusHours(2).plusMinutes(20));

        AuctionCardInfo card = assembler.assemble(closed, NOW, Map.of());

        assertThat(card.phase()).isEqualTo(RoomPhase.CLOSED);
        assertThat(card.connectedCount()).isZero();
    }

    // 개장은 시작 30분 전이라는 도메인 규칙을 그대로 따른다
    private static AuctionListRow row(long auctionId, LocalDateTime startAt, LocalDateTime endAt) {
        return new AuctionListRow(auctionId, 900L + auctionId, null, "HYUNDAI", "아반떼 CN7", 2022, 30_000,
                10_000_000L, 11_000_000L, startAt.minusMinutes(30), startAt, endAt);
    }

    // 어느 방이든 사람이 있다고 답한다. 0 을 돌려주면 단계 판정을 지워도 단정이 통과해 그물이 안 된다
    private static final class AlwaysWatchedRooms implements RoomChannel {

        @Override
        public int countViewers(long auctionId) {
            return WATCHING;
        }

        @Override
        public void subscribe(long auctionId, RoomSubscriber subscriber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean unsubscribe(long auctionId, RoomSubscriber subscriber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcast(long auctionId, RoomState state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Long> sweepClosed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Long> subscribedAuctions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeRoom(long auctionId) {
            throw new UnsupportedOperationException();
        }
    }
}
