package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

        // 현황에는 현재가 말고 보고 있는 사람 수와 호가창도 실린다, 같은 값을 막으면 그것들이 안 갱신된다
        assertThat(emitter.currentPrices()).containsExactly(20_000_000L, 20_000_000L);
    }

    private static RoomState stateAt(long currentPrice) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);

        return new RoomState(
                AUCTION,
                RoomPhase.LIVE,
                currentPrice,
                now.plusMinutes(19),
                now,
                1,
                new BidCounts(1, 1),
                null,
                List.of());
    }

    // 무엇을 실어 보냈는지 받아 적는다
    private static final class RecordingEmitter extends SseEmitter {

        private final List<Object> sent = new ArrayList<>();

        @Override
        public void send(Object object) {
            sent.add(object);
        }

        List<Long> currentPrices() {
            return sent.stream()
                    .map(RoomStateResponse.class::cast)
                    .map(RoomStateResponse::currentPrice)
                    .toList();
        }
    }
}
