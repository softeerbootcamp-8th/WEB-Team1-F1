package com.softeer.race.auctionroom.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 호가창에 나가는 값은 닉네임이 아니라 본명이라 앞뒤 한 글자만 남긴다
class MaskedNameTest {

    @DisplayName("앞뒤 한 글자만 남기고 가운데를 가린다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "김현, 김*",
            "김민현, 김*현",
            "남궁민수, 남**수"
    })
    void mask(String realName, String expected) {
        assertThat(new MaskedName(realName).value()).isEqualTo(expected);
    }

    @DisplayName("이름으로 쓸 수 없는 값은 거부한다")
    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "김"})
    void rejectUnusableName(String unusable) {
        assertThatThrownBy(() -> new MaskedName(unusable))
                .isInstanceOf(IllegalArgumentException.class);
    }
}