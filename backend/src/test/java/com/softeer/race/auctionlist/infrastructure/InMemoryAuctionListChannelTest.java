package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    // 연결 기술을 모르는 대역이다, 목으로 감싸면 명부에 실제로 들어갔는지 볼 수 없다
    private static final class FakeSubscriber implements AuctionListSubscriber {

        private boolean open = true;

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
