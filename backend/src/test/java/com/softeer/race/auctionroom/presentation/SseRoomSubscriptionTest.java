package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.application.ViewerCount;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import com.softeer.race.auctionroom.presentation.response.ViewerCountResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 낡은 현황을 거른다는 계약까지만 지킨다, 잠금을 빼도 이 테스트는 통과한다
// 검사와 전송의 원자성은 BroadcastOrderExperiment 가 확률로 잡는다
class SseRoomSubscriptionTest {

    private static final long AUCTION = 1L;
    private static final long VIEWER = 7L;

    @Test
    @DisplayName("더 높은 현재가를 보낸 뒤에는 낡은 현황을 보내지 않는다")
    void staleStateIsNotSent() {
        RecordingEmitter emitter = new RecordingEmitter();
        RoomSubscription subscription = new SseRoomSubscription(AUCTION, VIEWER, emitter);

        subscription.send(stateAt(20_000_000L));
        subscription.send(stateAt(15_000_000L));

        assertThat(emitter.currentPrices()).containsExactly(20_000_000L);
    }

    @Test
    @DisplayName("현재가가 같은 현황은 그대로 보낸다")
    void stateWithSameCurrentPriceIsSent() {
        RecordingEmitter emitter = new RecordingEmitter();
        RoomSubscription subscription = new SseRoomSubscription(AUCTION, VIEWER, emitter);

        subscription.send(stateAt(20_000_000L));
        subscription.send(stateAt(20_000_000L));

        // 현황에는 현재가 말고 호가창과 집계도 실린다, 같은 값을 막으면 그것들이 안 갱신된다
        assertThat(emitter.currentPrices()).containsExactly(20_000_000L, 20_000_000L);
    }

    @Test
    @DisplayName("나중 번호를 보낸 뒤에는 낡은 사람 수를 보내지 않는다")
    void staleViewerCountIsNotSent() {
        RecordingEmitter emitter = new RecordingEmitter();
        RoomSubscription subscription = new SseRoomSubscription(AUCTION, VIEWER, emitter);

        subscription.send(new ViewerCount(AUCTION, 2, 2L));
        subscription.send(new ViewerCount(AUCTION, 1, 1L));

        assertThat(emitter.viewerCounts()).containsExactly(2);
    }

    @Test
    @DisplayName("사람 수와 현황은 서로의 순서를 밀어내지 않는다")
    void viewerCountAndStateAreOrderedApart() {
        RecordingEmitter emitter = new RecordingEmitter();
        RoomSubscription subscription = new SseRoomSubscription(AUCTION, VIEWER, emitter);

        // 번호가 현재가보다 작아도 종류가 달라 서로를 낡게 만들지 않는다
        subscription.send(stateAt(20_000_000L));
        subscription.send(new ViewerCount(AUCTION, 3, 1L));

        assertThat(emitter.currentPrices()).containsExactly(20_000_000L);
        assertThat(emitter.viewerCounts()).containsExactly(3);
    }

    private static RoomState stateAt(long currentPrice) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);

        return new RoomState(
                AUCTION,
                RoomPhase.LIVE,
                currentPrice,
                now.plusMinutes(19),
                now,
                new BidCounts(1, 1),
                null,
                List.of());
    }

    // 무엇을 실어 보냈는지 받아 적는다
    // 이름 있는 이벤트는 send(Object) 가 아니라 send(SseEventBuilder) 로 가므로 둘 다 받아야 한다
    private static final class RecordingEmitter extends SseEmitter {

        private final List<Object> sent = new ArrayList<>();

        @Override
        public void send(Object object) {
            sent.add(object);
        }

        @Override
        public void send(SseEventBuilder builder) {
            builder.build().forEach(entry -> sent.add(entry.getData()));
        }

        List<Long> currentPrices() {
            return bodiesOf(RoomStateResponse.class).map(RoomStateResponse::currentPrice).toList();
        }

        List<Integer> viewerCounts() {
            return bodiesOf(ViewerCountResponse.class).map(ViewerCountResponse::viewerCount).toList();
        }

        // 이벤트 이름과 줄바꿈도 같은 목록에 섞여 들어온다, 본문 타입으로 골라낸다
        private <T> Stream<T> bodiesOf(Class<T> type) {
            return sent.stream().filter(type::isInstance).map(type::cast);
        }
    }
}
