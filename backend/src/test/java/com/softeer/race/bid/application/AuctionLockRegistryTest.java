package com.softeer.race.bid.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잠금 항목의 생애를 검증한다.
 * <p>
 * 조회용 API 를 열지 않으므로 항목의 생사는 acquire 가 같은 잠금 객체를 돌려주는지로 판별한다 -
 * 같은 객체면 항목이 살아 있고, 새 객체면 지워졌던 것이다.
 */
@DisplayName("경매 잠금 항목의 생애")
class AuctionLockRegistryTest {

    private static final long AUCTION_ID = 1000L;

    private final AuctionLockRegistry registry = new AuctionLockRegistry();

    // acquire 는 잠금 객체를 돌려줄 뿐 걸지는 않으므로 한 스레드에서 겹침을 흉내낼 수 있다
    @Test
    @DisplayName("겹치는 사용자들은 같은 잠금을 본다")
    void sharesLockWhileInUse() {
        ReentrantLock first = registry.acquire(AUCTION_ID);

        assertThat(registry.acquire(AUCTION_ID)).isSameAs(first);

        registry.release(AUCTION_ID);
        assertThat(registry.acquire(AUCTION_ID)).isSameAs(first);
    }

    @Test
    @DisplayName("마지막 반납이 항목을 지운다")
    void removesEntryOnLastRelease() {
        ReentrantLock first = registry.acquire(AUCTION_ID);
        registry.release(AUCTION_ID);

        assertThat(registry.acquire(AUCTION_ID)).isNotSameAs(first);
    }

    @Test
    @DisplayName("경매가 다르면 잠금도 다르다")
    void separatesLocksByAuction() {
        assertThat(registry.acquire(AUCTION_ID)).isNotSameAs(registry.acquire(2000L));
    }
}
