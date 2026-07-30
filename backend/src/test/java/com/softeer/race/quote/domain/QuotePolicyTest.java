package com.softeer.race.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuotePolicyTest {

    @DisplayName("기준가에서 연식 감가와 주행거리 감가를 각각 뺀다")
    @ParameterizedTest(name = "기준가 {0}원 {1}년 {2}km 면 {3}원")
    @CsvSource({
            // 신차는 감가가 없어 기준가 그대로다
            "34000000,  0,       0,  34000000",
            // 주행거리만 있는 경우, 1만km 는 기준가의 1.5%
            "34000000,  0,   10000,  33490000",
            // 연식만 있는 경우, 1년은 기준가의 5%
            "34000000,  1,       0,  32300000",
            // 픽스처의 그랜저 IG, 두 감가가 함께 걸린다
            "34000000,  5,   45000,  23200000",
            // 픽스처의 쏘렌토
            "40000000,  4,   32000,  30080000",
            // 픽스처의 520i
            "68000000,  6,   61000,  41370000"
    })
    void estimate(long basePrice, int age, int mileage, long expected) {
        assertThat(QuotePolicy.estimate(basePrice, age, mileage)).isEqualTo(expected);
    }

    // 하한선이 없으면 음수 시세가 그대로 응답에 실린다
    @DisplayName("감가 합계가 기준가를 넘기면 기준가의 20%로 막는다")
    @ParameterizedTest(name = "기준가 {0}원 {1}년 {2}km 면 {3}원")
    @CsvSource({
            // 픽스처의 아반떼 MD, 감가 합계 2304만이 기준가 1800만을 넘긴다
            "18000000,  16,  320000,   3600000",
            // 감가가 아무리 커져도 하한 아래로는 내려가지 않는다
            "18000000,  99,  999999,   3600000",
            // 경계, 감가 합계가 기준가의 80%와 정확히 같으면 아직 하한이 아니다
            "10000000,  16,       0,   2000000"
    })
    void flooredAtTwentyPercent(long basePrice, int age, int mileage, long expected) {
        assertThat(QuotePolicy.estimate(basePrice, age, mileage)).isEqualTo(expected);
    }

    @DisplayName("만원 미만은 버린다")
    @ParameterizedTest(name = "기준가 {0}원 {1}년 {2}km 면 {3}원")
    @CsvSource({
            // 감가 후 945만5천원, 5천원이 잘린다
            "10000000,  1,  3000,  9450000",
            // 감가 후 3399만4천9백원
            "34000000,  0,   100,  33990000"
    })
    void roundsDownToTenThousand(long basePrice, int age, int mileage, long expected) {
        assertThat(QuotePolicy.estimate(basePrice, age, mileage)).isEqualTo(expected);
    }

    // 하한도 만원 단위로 내려야 결과가 하한보다 낮아지지 않는다
    @DisplayName("하한선 자체도 만원 미만을 버린 값이다")
    @Test
    void floorIsAlsoRoundedDown() {
        // given : 기준가의 20%가 200만 1천원이다
        long basePrice = 10_005_000L;

        // when : 감가가 기준가를 한참 넘겨 하한에 걸린다
        long estimated = QuotePolicy.estimate(basePrice, 99, 999_999);

        // then : 200만 1천원이 아니라 200만원
        assertThat(estimated).isEqualTo(2_000_000L);
    }

    // 시드에 출고 예정 연식이나 음수 주행거리가 들어가면 감가가 가산으로 뒤집혀
    // 기준가보다 높은 시세가 나간다
    @DisplayName("나이나 주행거리가 음수여도 기준가를 넘지 않는다")
    @ParameterizedTest(name = "{0}년 {1}km")
    @CsvSource({
            "-2,       0",
            " 0,  -50000",
            "-2,  -50000"
    })
    void negativeInputsDoNotIncreasePrice(int age, int mileage) {
        assertThat(QuotePolicy.estimate(34_000_000L, age, mileage)).isEqualTo(34_000_000L);
    }
}
