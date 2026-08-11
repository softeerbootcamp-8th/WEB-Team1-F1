package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 목록 구독은 전역이라 명부가 집합 하나다, 경매별 엔트리 생성·소멸이 없어 방 채널의 compute 를 물려받지 않는다
@DisplayName("목록 구독 명부 테스트")
class InMemoryAuctionListChannelTest {

    private final InMemoryAuctionListChannel channel = new InMemoryAuctionListChannel();

    @Test
    @DisplayName("구독을 등록하면 보는 사람이 있다")
    void subscribedChannelHasSubscribers() {
        channel.subscribe(new FakeSubscriber());

        assertThat(channel.hasSubscribers()).isTrue();
    }

    @Test
    @DisplayName("마지막 구독을 해제하면 보는 사람이 없다")
    void unsubscribingLastLeavesNobody() {
        AuctionListSubscriber subscriber = new FakeSubscriber();
        channel.subscribe(subscriber);

        channel.unsubscribe(subscriber);

        assertThat(channel.hasSubscribers()).isFalse();
    }

    @Test
    @DisplayName("둘 중 하나만 해제하면 남은 구독이 있다")
    void unsubscribingOneKeepsTheOther() {
        AuctionListSubscriber leaving = new FakeSubscriber();
        channel.subscribe(leaving);
        channel.subscribe(new FakeSubscriber());

        channel.unsubscribe(leaving);

        assertThat(channel.hasSubscribers()).isTrue();
    }

    @Test
    @DisplayName("등록한 구독이 카드를 받는다")
    void subscriberReceivesCard() {
        FakeSubscriber subscriber = new FakeSubscriber();
        channel.subscribe(subscriber);

        channel.broadcastCard(card(1L));

        assertThat(subscriber.received).extracting(AuctionCardInfo::auctionId).containsExactly(1L);
    }

    @Test
    @DisplayName("전송 중 닫힌 구독은 걷어내고 연결도 끝낸다")
    void closedDuringSendIsDiscarded() {
        FakeSubscriber dying = new FakeSubscriber();
        dying.diesOnSend = true;
        channel.subscribe(dying);

        channel.broadcastCard(card(1L));

        // 명부에서 빼기만 하면 그 응답은 아무도 끝내지 않아 만료까지 산다
        assertThat(channel.hasSubscribers()).isFalse();
        assertThat(dying.closed).isTrue();
    }

    @Test
    @DisplayName("걷어낼 때 명부를 먼저 비운다")
    void registryIsClearedBeforeClosing() {
        FakeSubscriber dying = new FakeSubscriber();
        dying.diesOnSend = true;
        channel.subscribe(dying);

        channel.broadcastCard(card(1L));

        // 끝난 연결의 해제 콜백이 돌아왔을 때 할 일이 남아 있으면 안 된다
        assertThat(dying.listedWhenClosed).isFalse();
    }

    @Test
    @DisplayName("시청자 수 전송도 카드와 같은 걷어내기를 탄다")
    void audienceBroadcastDiscardsClosedToo() {
        FakeSubscriber dying = new FakeSubscriber();
        dying.diesOnSend = true;
        channel.subscribe(dying);

        channel.broadcastAudience(1L, 3);

        // 보낼 것이 둘이라 정리를 각자 하면 한쪽 경로에서만 응답이 만료까지 살아남는다
        assertThat(channel.hasSubscribers()).isFalse();
        assertThat(dying.closed).isTrue();
    }

    @Test
    @DisplayName("찔러 보다 드러난 죽은 구독을 걷어낸다")
    void sweepDiscardsSubscriberFoundDead() {
        // 서버는 이 연결에 쓰기만 하고 읽지 않아, 상대가 끊어도 다음 쓰기 전까지 모른다
        FakeSubscriber dying = new FakeSubscriber();
        dying.diesOnPing = true;
        channel.subscribe(dying);

        channel.sweepClosed();

        assertThat(channel.hasSubscribers()).isFalse();
        assertThat(dying.closed).isTrue();
    }

    @Test
    @DisplayName("살아 있는 구독은 걷어내기에서 살아남는다")
    void sweepKeepsOpenSubscriber() {
        FakeSubscriber alive = new FakeSubscriber();
        channel.subscribe(alive);

        channel.sweepClosed();

        // 아무 일 없는 동안 연결을 유지하는 것도 이 주기의 몫이라, 멀쩡한 구독을 끊으면 안 된다
        assertThat(channel.hasSubscribers()).isTrue();
        assertThat(alive.closed).isFalse();
        assertThat(alive.pinged).isTrue();
    }

    // 채널은 카드 내용을 보지 않고 그대로 넘기기만 하므로 식별자만 채운다
    private static AuctionCardInfo card(long auctionId) {
        return new AuctionCardInfo(auctionId, null, null, null, null, null, null,
                List.of(), null, null, null, null, null, 0);
    }

    // 연결 기술을 모르는 대역이다, 목으로 감싸면 명부에 실제로 들어갔는지 볼 수 없다
    private final class FakeSubscriber implements AuctionListSubscriber {

        private final List<AuctionCardInfo> received = new ArrayList<>();

        private boolean open = true;
        private boolean diesOnSend;
        private boolean diesOnPing;
        private boolean pinged;
        private boolean closed;
        private boolean listedWhenClosed = true;

        @Override
        public void sendCard(AuctionCardInfo card) {
            received.add(card);

            if (diesOnSend) {
                open = false;
            }
        }

        @Override
        public void sendAudience(long auctionId, int connectedCount) {
            if (diesOnSend) {
                open = false;
            }
        }

        @Override
        public void ping() {
            pinged = true;

            if (diesOnPing) {
                open = false;
            }
        }

        @Override
        public void close() {
            open = false;
            closed = true;
            listedWhenClosed = channel.hasSubscribers();
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
