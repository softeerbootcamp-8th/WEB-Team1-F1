package com.softeer.race.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 화면에 나가는 값은 닉네임이 아니라 본명이라 앞뒤 한 글자만 남긴다
// 경매방 호가창과 거래 상대 이름이 같은 규칙을 쓴다
class MaskedNameTest {

    @DisplayName("앞뒤 한 글자만 남기고 가운데를 가린다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "김현, 김*",
            "김민현, 김*현",
            "남궁민수, 남**수"
    })
    void mask(String realName, String expected) {
        assertThat(MaskedName.mask(realName).value()).isEqualTo(expected);
    }

    @DisplayName("이름으로 쓸 수 없는 값은 거부한다")
    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "김"})
    void rejectUnusableName(String unusable) {
        assertThatThrownBy(() -> MaskedName.mask(unusable))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가릴 수 없는 이름을 거부할 때 예외 메시지에 원본을 남기지 않는다")
    void errorMessageHidesRealName() {
        assertThatThrownBy(() -> MaskedName.mask("김"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("김");
    }
}
