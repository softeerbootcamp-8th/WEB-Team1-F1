package com.softeer.race.deal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거래 단계 전이 규칙
 * <p>
 * 규칙을 표가 아니라 전수 분기로 적은 결정을 여기서 지킨다. 단계를 추가하면 분기가 빌드에서
 * 먼저 깨지지만, 규칙을 잘못 적는 것까지 막지는 못한다.
 */
@DisplayName("거래 단계 전이 규칙")
class DealStatusTest {

    @ParameterizedTest(name = "{0} 다음은 {1} 하나뿐이다")
    @CsvSource({
            "DEPOSIT_PENDING, DOCUMENT_PENDING",
            "DOCUMENT_PENDING, TRANSPORT_PENDING",
            "TRANSPORT_PENDING, BALANCE_PENDING",
            "BALANCE_PENDING, HANDOVER_PENDING",
            "HANDOVER_PENDING, SETTLING",
            "SETTLING, COMPLETED"
    })
    @DisplayName("정해진 다음 단계로만 넘어간다")
    void 정해진_다음_단계로만_간다(DealStatus from, DealStatus next) {
        assertThat(from.canTransitionTo(next)).isTrue();

        // 나머지는 전부 막힌다, 건너뛰기도 되돌아가기도 성립하지 않는다
        assertThat(Arrays.stream(DealStatus.values()).filter(target -> target != next))
                .allSatisfy(target -> assertThat(from.canTransitionTo(target)).isFalse());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DealStatus.class, names = {"COMPLETED", "CANCELLED"})
    @DisplayName("끝난 거래는 어디로도 가지 않는다")
    void 끝난_거래는_어디로도_가지_않는다(DealStatus terminal) {
        assertThat(Arrays.stream(DealStatus.values()))
                .allSatisfy(target -> assertThat(terminal.canTransitionTo(target)).isFalse());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DealStatus.class)
    @DisplayName("정상 전이로는 취소에 도달할 수 없다")
    void 정상_전이로는_취소에_도달할_수_없다(DealStatus from) {
        // 이 판정을 통과한 전이는 사유를 받지 않는다, 참이 되는 순간 사유 없는 취소가 성립한다
        assertThat(from.canTransitionTo(DealStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DealStatus.class,
            names = {"DEPOSIT_PENDING", "DOCUMENT_PENDING", "TRANSPORT_PENDING", "BALANCE_PENDING"})
    @DisplayName("잔금을 받기 전까지는 취소할 수 있다")
    void 잔금_전까지는_취소할_수_있다(DealStatus status) {
        assertThat(status.isCancellable()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DealStatus.class,
            names = {"HANDOVER_PENDING", "SETTLING", "COMPLETED", "CANCELLED"})
    @DisplayName("잔금을 받은 뒤로는 취소할 수 없다")
    void 잔금_이후로는_취소할_수_없다(DealStatus status) {
        // 되돌리려면 환불·재탁송·명의 원복이 따라온다, 취소와 다른 흐름이다
        assertThat(status.isCancellable()).isFalse();
    }
}
