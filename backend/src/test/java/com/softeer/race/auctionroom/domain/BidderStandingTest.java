package com.softeer.race.auctionroom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 순위는 "나보다 높이 부른 사람 수" 로 셈한다, 그 한 칸을 더하는 규칙이 여기 있다
class BidderStandingTest {

    private static final long MY_HIGHEST_AMOUNT = 9_300_000L;

    @Test
    @DisplayName("나보다 높이 부른 사람이 없으면 1등이다")
    void topBidderIsFirst() {
        assertThat(BidderStanding.of(MY_HIGHEST_AMOUNT, 0).rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("나보다 높이 부른 사람 수만큼 순위가 밀린다")
    void rankFollowsBiddersAbove() {
        assertThat(BidderStanding.of(MY_HIGHEST_AMOUNT, 1).rank()).isEqualTo(2);
        assertThat(BidderStanding.of(MY_HIGHEST_AMOUNT, 3).rank()).isEqualTo(4);
    }
}
