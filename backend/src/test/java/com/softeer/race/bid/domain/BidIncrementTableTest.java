package com.softeer.race.bid.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BidIncrementTableTest {

    /** 픽스처(bid-increment-bands.sql)와 같은 구간표 */
    private static final BidIncrementTable TABLE = new BidIncrementTable(List.of(
            new BidIncrementBand(0, 10_000),
            new BidIncrementBand(5_000_000, 50_000),
            new BidIncrementBand(30_000_000, 100_000),
            new BidIncrementBand(60_000_000, 200_000),
            new BidIncrementBand(100_000_000, 500_000)
    ));

    @DisplayName("이후 입찰의 최소 금액은 현재가에 그 구간의 상승가를 더한 값이다")
    @ParameterizedTest(name = "현재가 {0}원이면 {1}원")
    @CsvSource({
            // 최하단 구간(+1만)
            "0,          10000",
            "10000,      20000",
            // 하한은 다음 구간에 속한다, 500만은 +1만이 아니라 +5만이다
            "4990000,    5000000",
            "5000000,    5050000",
            // 격자에서 벗어난 현재가도 구간 상승가를 그대로 더한다, 505만이 아니라 506만
            "5010000,    5060000",
            // 구간 경계는 넘겨서 착지한다, 아직 5만 구간이라 3000만이 아니라 3004만
            "29990000,   30040000",
            // 구간3(+10만), 구간4(+20만), 최상단 구간(+50만, 상한 없음)
            "30000000,   30100000",
            "60000000,   60200000",
            "100000000,  100500000",
            "400000000,  400500000"
    })
    void minAmountOfNextBid(long currentPrice, long expected) {
        // 현재가가 있으면 시작가는 판정에 쓰이지 않는다
        assertThat(TABLE.ruleFor(0, currentPrice).minAmount()).isEqualTo(expected);
    }

    // 첫 입찰만 한 칸 올리지 않고 그대로 낼 수 있다, 시작가가 상승가 격자에 맞지 않아도 마찬가지다
    @DisplayName("입찰이 없으면 최소 금액은 시작가 그대로다")
    @Test
    void minAmountOfFirstBid() {
        BidRule rule = TABLE.ruleFor(24_800_000, null);

        assertThat(rule.currentPrice()).isEqualTo(24_800_000);
        assertThat(rule.minAmount()).isEqualTo(24_800_000);
        assertThat(rule.increment()).isEqualTo(50_000);
    }

    @DisplayName("구간표를 어떤 순서로 받아도 결과가 같다")
    @Test
    void orderDoesNotMatter() {
        BidIncrementTable shuffled = new BidIncrementTable(List.of(
                new BidIncrementBand(30_000_000, 100_000),
                new BidIncrementBand(0, 10_000),
                new BidIncrementBand(5_000_000, 50_000)
        ));

        assertThat(shuffled.ruleFor(0, 10_000_000L).minAmount()).isEqualTo(10_050_000);
    }

    // 조회 API가 하한 오름차순을 계약으로 내걸었으므로 정렬 자체도 고정한다
    @DisplayName("구간표는 하한 오름차순으로 정렬되어 반환된다")
    @Test
    void bandsAreSortedByMinPrice() {
        BidIncrementTable shuffled = new BidIncrementTable(List.of(
                new BidIncrementBand(30_000_000, 100_000),
                new BidIncrementBand(0, 10_000),
                new BidIncrementBand(5_000_000, 50_000)
        ));

        assertThat(shuffled.getBands())
                .extracting(BidIncrementBand::getMinPrice)
                .containsExactly(0L, 5_000_000L, 30_000_000L);
    }

    @DisplayName("현재가를 담당하는 구간이 없으면 예외를 던진다")
    @Test
    void bandNotFound() {
        BidIncrementTable withGap = new BidIncrementTable(List.of(
                new BidIncrementBand(5_000_000, 50_000)
        ));

        assertThatThrownBy(() -> withGap.ruleFor(0, 1_000_000L))
                .isInstanceOf(IllegalStateException.class);
    }
}
