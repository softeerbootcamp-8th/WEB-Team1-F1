package com.softeer.race.auctionroom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 단계 이름은 응답에 그대로 실려 FE 화면 분기의 기준이 된다
class RoomPhaseTest {

    @Test
    @DisplayName("방 단계 이름과 순서는 화면 계약이라 바뀌면 안 된다")
    void phaseNamesArePartOfTheContract() {
        assertThat(RoomPhase.values())
                .extracting(Enum::name)
                .containsExactly("NOT_OPEN", "WAITING", "LIVE", "RESULT", "CLOSED");
    }
}
