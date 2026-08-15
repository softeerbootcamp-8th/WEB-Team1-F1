package com.softeer.race.bid.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softeer.race.bid.exception.BidErrorCode;
import com.softeer.race.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BidRuleTest {

    /** 현재가 2480만, 그 구간의 상승가 5만, 그래서 최소 금액은 2485만 */
    private static final BidRule NEXT_BID = new BidRule(24_800_000, 50_000, 24_850_000);

    /** 입찰이 아직 없는 경매, 시작가를 그대로 낼 수 있어 최소 금액이 현재가와 같다 */
    private static final BidRule FIRST_BID = new BidRule(24_800_000, 50_000, 24_800_000);

    @DisplayName("최소 금액에서 상승가 배수만큼 올린 금액은 성립한다")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {
            24_850_000,   // 한 칸
            24_900_000,   // 두 칸
            25_850_000    // 스물한 칸, 칸 수에는 상한이 없다
    })
    void acceptsAlignedAmount(long amount) {
        assertThatCode(() -> NEXT_BID.validate(amount)).doesNotThrowAnyException();
    }

    // 첫 입찰만 한 칸 올리지 않아도 된다, 시작가가 상승가 격자에 맞지 않아도 마찬가지다
    @DisplayName("첫 입찰은 시작가를 그대로 낼 수 있다")
    @Test
    void acceptsStartPriceOnFirstBid() {
        assertThatCode(() -> FIRST_BID.validate(24_800_000)).doesNotThrowAnyException();
    }

    @DisplayName("최소 금액에 못 미치면 거절한다")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {
            24_800_000,   // 현재가와 같은 금액, 배수 조건은 만족하지만 올리지 않았다
            24_840_000
    })
    void rejectsAmountBelowMinimum(long amount) {
        assertRejectedWith(amount, BidErrorCode.BID_AMOUNT_TOO_LOW);
    }

    // 화면이 +버튼만 제공해도 API 는 열려 있다, 임의 금액은 서버가 막는다
    @DisplayName("상승가 배수가 아니면 거절한다")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {
            24_870_000,   // 현재가에서 7만, 5만의 배수가 아니다
            24_880_000
    })
    void rejectsMisalignedAmount(long amount) {
        assertRejectedWith(amount, BidErrorCode.BID_AMOUNT_NOT_ALIGNED);
    }

    // 두 조건을 다 어긴 금액으로 판정 순서를 고정한다
    // 한쪽만 어긴 금액은 순서와 무관하게 같은 사유가 나오므로 이 케이스에서만 순서가 드러난다
    // 순서를 뒤집어도 컴파일되고 다른 테스트도 통과하므로, 여기서 막지 않으면 조용히 바뀐다
    @DisplayName("최소 금액에도 못 미치고 배수도 아니면 금액 미달로 거절한다")
    @Test
    void reportsTooLowBeforeMisaligned() {
        // 2483만은 2485만보다 낮고, 현재가와의 차이 3만도 5만의 배수가 아니다
        assertRejectedWith(24_830_000, BidErrorCode.BID_AMOUNT_TOO_LOW);
    }

    // 아래 둘은 구간표가 깨졌을 때만 발생한다
    // 사용자가 고칠 수 없는 서버 데이터 파손이라 BusinessException 이 아니어야 한다
    @DisplayName("상승가가 0 이하면 규칙을 만들 수 없다")
    @Test
    void rejectsNonPositiveIncrement() {
        assertThatThrownBy(() -> new BidRule(24_800_000, 0, 24_800_000))
                .isInstanceOf(IllegalStateException.class);
    }

    // ruleFor 가 minAmount 를 잘못 계산했을 때만 걸리는 조립 단계 검사다
    // 이 상태를 허용하면 현재가와 최소 금액 사이 금액이 하한을 통과해 배수 판정이 의미를 잃는다
    @DisplayName("최소 금액이 현재가보다 낮으면 규칙을 만들 수 없다")
    @Test
    void rejectsMinAmountBelowCurrentPrice() {
        assertThatThrownBy(() -> new BidRule(24_800_000, 50_000, 24_750_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("상한을 넘는 금액은 배수가 맞아도 거절한다")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {
            1_000_000_050_000L,   // 상한 바로 위, 배수는 맞는 금액
            1_000_000_030_000L    // 상한 위이고 배수도 어긋난 금액
    })
    void rejectsAmountAboveCap(long amount) {
        assertRejectedWith(amount, BidErrorCode.BID_AMOUNT_TOO_HIGH);
    }

    @DisplayName("상한과 같은 금액은 성립한다")
    @Test
    void acceptsAmountAtCap() {
        BidRule atCap = new BidRule(999_999_950_000L, 50_000, 1_000_000_000_000L);

        assertThatCode(() -> atCap.validate(1_000_000_000_000L)).doesNotThrowAnyException();
    }

    private static void assertRejectedWith(long amount, BidErrorCode expected) {
        assertThatThrownBy(() -> NEXT_BID.validate(amount))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(expected);
    }
}
