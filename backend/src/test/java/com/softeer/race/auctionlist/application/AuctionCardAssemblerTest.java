package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.domain.AuctionListRow;
import com.softeer.race.auctionroom.application.RoomChannel;
import com.softeer.race.auctionroom.application.RoomMessage;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.application.ViewerCount;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 조회와 방송이 같은 카드를 내야 하므로 조립 규칙을 여기서 못 박는다
@DisplayName("목록 카드 조립 테스트")
class AuctionCardAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);
    private static final int WATCHING = 4;

    // 차량 id 를 경매 id 와 다른 대역에 둔다, 같게 두면 둘을 바꿔 껴도 통과한다
    private static final long VEHICLE_ID_BASE = 900L;
    private static final long OTHER_VEHICLE_ID = 777L;

    private final AuctionCardAssembler assembler = new AuctionCardAssembler(new AlwaysWatchedRooms());

    @Test
    @DisplayName("열린 단계는 경매방의 접속자 수를 그대로 쓴다")
    void openPhaseUsesRoomCount() {
        AuctionListRow live = row(1L, NOW.minusMinutes(10), NOW.plusMinutes(10));

        AuctionCardInfo card = assembler.assemble(live, NOW, Map.of());

        assertThat(card.phase()).isEqualTo(RoomPhase.LIVE);
        assertThat(card.viewerCount()).isEqualTo(WATCHING);
    }

    @Test
    @DisplayName("닫힌 단계는 방에 사람이 남아 있어도 0이다")
    void closedPhaseCountsNobody() {
        AuctionListRow closed = row(2L, NOW.minusHours(2), NOW.minusHours(2).plusMinutes(20));

        AuctionCardInfo card = assembler.assemble(closed, NOW, Map.of());

        assertThat(card.phase()).isEqualTo(RoomPhase.CLOSED);
        assertThat(card.viewerCount()).isZero();
    }

    @Test
    @DisplayName("그 차량의 키워드가 카드에 붙는다")
    void attachesKeywordsOfThatVehicle() {
        AuctionListRow live = row(1L, NOW.minusMinutes(10), NOW.plusMinutes(10));

        AuctionCardInfo card = assembler.assemble(live, NOW, Map.of(
                live.vehicleId(), List.of(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.UNDERBODY_INTACT)));

        assertThat(card.keywords())
                .containsExactly(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.UNDERBODY_INTACT);
    }

    @Test
    @DisplayName("키워드가 없는 차량은 null 이 아니라 빈 목록이다")
    void vehicleWithoutKeywordsGetsEmptyList() {
        AuctionListRow live = row(1L, NOW.minusMinutes(10), NOW.plusMinutes(10));

        // 다른 차량 것만 담긴 맵이다, 남의 키워드가 새어 들어오지 않아야 한다
        AuctionCardInfo card = assembler.assemble(live, NOW,
                Map.of(OTHER_VEHICLE_ID, List.of(VehicleKeyword.ACCIDENT_FREE)));

        assertThat(card.keywords()).isEmpty();
    }

    // 개장은 시작 30분 전이라는 도메인 규칙을 그대로 따른다
    private static AuctionListRow row(long auctionId, LocalDateTime startAt, LocalDateTime endAt) {
        return new AuctionListRow(auctionId, VEHICLE_ID_BASE + auctionId, null, "HYUNDAI", "아반떼 CN7",
                2022, 30_000, 10_000_000L, 11_000_000L, startAt.minusMinutes(30), startAt, endAt);
    }

    // 어느 방이든 사람이 있다고 답한다. 0 을 돌려주면 단계 판정을 지워도 단정이 통과해 그물이 안 된다
    private static final class AlwaysWatchedRooms implements RoomChannel {

        @Override
        public int viewerCount(long auctionId) {
            return WATCHING;
        }

        @Override
        public ViewerCount readViewerCount(long auctionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, Integer> viewerCountByRoom() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void subscribe(long auctionId, RoomSubscription subscription) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean unsubscribe(long auctionId, RoomSubscription subscription) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void broadcast(long auctionId, RoomMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void catchUp(long auctionId, RoomSubscription subscription) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Long> sweepClosed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Long> subscribedRooms() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeRoom(long auctionId) {
            throw new UnsupportedOperationException();
        }
    }
}
