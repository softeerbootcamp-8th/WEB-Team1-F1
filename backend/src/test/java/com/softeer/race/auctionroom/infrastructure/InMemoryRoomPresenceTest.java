package com.softeer.race.auctionroom.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 조회 요청 자체가 하트비트라, 같은 사람이 몇 번을 조회해도 한 명이어야 한다
class InMemoryRoomPresenceTest {

    private static final long AUCTION = 1L;
    private static final long OTHER_AUCTION = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 20, 0);

    private final InMemoryRoomPresence presence = new InMemoryRoomPresence();

    @Test
    @DisplayName("같은 사용자가 여러 번 조회해도 한 명으로 센다")
    void sameUserCountedOnce() {
        presence.markPresent(AUCTION, 1L, NOW);
        presence.markPresent(AUCTION, 1L, NOW.plusSeconds(2));

        long connected = presence.countPresent(AUCTION, NOW.plusSeconds(4));

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 사용자는 각각 센다")
    void differentUsersCountedSeparately() {
        presence.markPresent(AUCTION, 1L, NOW);
        presence.markPresent(AUCTION, 2L, NOW);

        long connected = presence.countPresent(AUCTION, NOW);

        assertThat(connected).isEqualTo(2);
    }

    @Test
    @DisplayName("유효시간이 지나면 접속자에서 빠진다")
    void expiredUserDropsOut() {
        presence.markPresent(AUCTION, 1L, NOW);

        long connected = presence.countPresent(AUCTION, NOW.plusSeconds(11));

        assertThat(connected).isEqualTo(0);
    }

    @Test
    @DisplayName("유효시간 정각까지는 접속자로 남는다")
    void userSurvivesUntilTtlBoundary() {
        presence.markPresent(AUCTION, 1L, NOW);

        long connected = presence.countPresent(AUCTION, NOW.plusSeconds(10));

        assertThat(connected).isEqualTo(1);
    }

    @Test
    @DisplayName("경매방끼리 접속자가 섞이지 않는다")
    void roomsAreIsolated() {
        presence.markPresent(AUCTION, 1L, NOW);
        presence.markPresent(AUCTION, 2L, NOW);

        long connected = presence.countPresent(OTHER_AUCTION, NOW);

        assertThat(connected).isEqualTo(0);
    }

    @Test
    @DisplayName("아무도 없는 방은 0명이다")
    void emptyRoomCountsZero() {
        long connected = presence.countPresent(AUCTION, NOW);

        assertThat(connected).isEqualTo(0);
    }
}