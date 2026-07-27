package com.softeer.race.bid.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BidIncrementPolicyTest {

    /** data.sql과 같은 구간표 */
    private static final BidIncrementPolicy POLICY = new BidIncrementPolicy(List.of(
            new BidIncrementTier(0, 10_000),
            new BidIncrementTier(5_000_000, 50_000),
            new BidIncrementTier(30_000_000, 100_000),
            new BidIncrementTier(60_000_000, 200_000),
            new BidIncrementTier(100_000_000, 500_000)
    ));

    @DisplayName("다음 최소 입찰가는 해당 구간 상승가의 배수 중 현재가보다 큰 최솟값이다")
    @ParameterizedTest(name = "현재가 {0}원이면 {1}원")
    @CsvSource({
            // 최하단
            "0,          10000",
            "1,          10000",
            // 현재가가 이미 격자 위에 있으면 다음 칸으로 올라간다
            "10000,      20000",
            // 격자 밖 금액은 격자로 정렬된다
            "4999999,    5000000",
            // 구간1에서 계산한 결과가 구간2의 하한과 맞아떨어진다
            "4990000,    5000000",
            // 하한은 다음 구간에 속한다, 상승가가 1만이 아니라 5만이어야 한다
            "5000000,    5050000",
            // 구간2 -> 구간3
            "29990000,   30000000",
            "30000000,   30100000",
            // 구간3 -> 구간4
            "59900000,   60000000",
            // 구간4 -> 구간5
            "99800000,   100000000",
            // 최상단, 상한이 없다
            "100000000,  100500000",
            "400000000,  400500000"
    })
    void nextBidPrice(long currentPrice, long expected) {
        assertThat(POLICY.nextBidPrice(currentPrice)).isEqualTo(expected);
    }

    @DisplayName("구간표를 어떤 순서로 받아도 결과가 같다")
    @Test
    void orderDoesNotMatter() {
        BidIncrementPolicy shuffled = new BidIncrementPolicy(List.of(
                new BidIncrementTier(30_000_000, 100_000),
                new BidIncrementTier(0, 10_000),
                new BidIncrementTier(5_000_000, 50_000)
        ));

        assertThat(shuffled.nextBidPrice(10_000_000)).isEqualTo(10_050_000);
    }

    @DisplayName("현재가를 담당하는 구간이 없으면 예외를 던진다")
    @Test
    void tierNotFound() {
        BidIncrementPolicy withGap = new BidIncrementPolicy(List.of(
                new BidIncrementTier(5_000_000, 50_000)
        ));

        assertThatThrownBy(() -> withGap.nextBidPrice(1_000_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
