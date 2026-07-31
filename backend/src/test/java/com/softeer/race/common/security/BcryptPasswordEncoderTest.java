package com.softeer.race.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BcryptPasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new BcryptPasswordEncoder();

    @Test
    @DisplayName("비밀번호를 원문과 다른 BCrypt 해시로 만들고 검증할 수 있다")
    void encodeAndMatches() {
        String rawPassword = "password123";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
    }

    @Test
    @DisplayName("같은 비밀번호도 salt에 따라 다른 해시가 생성되고 모두 검증된다")
    void encodeUsesRandomSalt() {
        String rawPassword = "password123";

        String firstEncodedPassword = passwordEncoder.encode(rawPassword);
        String secondEncodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(firstEncodedPassword).isNotEqualTo(secondEncodedPassword);
        assertThat(passwordEncoder.matches(rawPassword, firstEncodedPassword)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, secondEncodedPassword)).isTrue();
    }

    // SignUpRequest가 허용하는 최대 길이. ASCII 전용이라 64자 = 64바이트로 bcrypt의 72바이트 한계 안에 든다
    @Test
    @DisplayName("허용 상한인 64자 비밀번호를 처리할 수 있다")
    void encodeMaximumLengthPassword() {
        String rawPassword = "a".repeat(64);

        assertThatCode(() -> passwordEncoder.encode(rawPassword))
                .doesNotThrowAnyException();
    }

    // 로그인 요청에는 ASCII 제약이 없어 64자 한글(192바이트)이 들어올 수 있다
    // AuthService가 검증 전에 바이트 길이를 막는 근거가 이 경계이므로 여기서 고정한다
    @Test
    @DisplayName("검증은 72바이트까지 허용하고 그 이상은 예외를 던진다")
    void matchesRejectsInputBeyondByteLimit() {
        String encodedPassword = passwordEncoder.encode("password123");

        assertThatCode(() -> passwordEncoder.matches("a".repeat(72), encodedPassword))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> passwordEncoder.matches("a".repeat(73), encodedPassword))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
