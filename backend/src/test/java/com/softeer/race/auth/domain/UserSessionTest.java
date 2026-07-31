package com.softeer.race.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.softeer.race.user.domain.User;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSessionTest {

    private static final String HASHED_TOKEN = "0".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0, 0);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration RENEW_THRESHOLD = Duration.ofMinutes(15);

    @Test
    @DisplayName("발급하면 만료 시각이 현재 시각 + TTL로 잡히고 PK에 해시가 담긴다")
    void issue() {
        UserSession session = issueAt(NOW);

        assertThat(session.getId()).isEqualTo(HASHED_TOKEN);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    // 경계를 만료 쪽으로 두지 않으면 만료 시각 정각에 세션이 한 순간 유효해진다
    @Test
    @DisplayName("만료 시각에 정확히 도달한 순간부터 만료로 본다")
    void isExpiredAtExactExpiryTime() {
        UserSession session = issueAt(NOW.minus(TTL));

        assertThat(session.getExpiresAt()).isEqualTo(NOW);
        assertThat(session.isExpired(NOW)).isTrue();
    }

    @Test
    @DisplayName("만료 시각 1초 전에는 만료가 아니다")
    void isNotExpiredBeforeExpiryTime() {
        UserSession session = issueAt(NOW.minus(TTL));

        assertThat(session.isExpired(NOW.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("남은 시간이 임계값과 정확히 같으면 연장 대상이다")
    void needsExtensionAtExactThreshold() {
        // 남은 시간이 정확히 15분이 되도록 15분 전에 발급된 세션
        UserSession session = issueAt(NOW.minus(TTL).plus(RENEW_THRESHOLD));

        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(RENEW_THRESHOLD));
        assertThat(session.needsExtension(NOW, RENEW_THRESHOLD)).isTrue();
    }

    @Test
    @DisplayName("남은 시간이 임계값보다 많으면 연장 대상이 아니다")
    void doesNotNeedExtensionBeyondThreshold() {
        // 남은 시간이 임계값보다 1초 많도록 1초 늦게 발급된 세션
        UserSession session = issueAt(NOW.minus(TTL).plus(RENEW_THRESHOLD).plusSeconds(1));

        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(RENEW_THRESHOLD).plusSeconds(1));
        assertThat(session.needsExtension(NOW, RENEW_THRESHOLD)).isFalse();
    }

    // 남은 시간에 더하는 방식이면 자주 접속한 세션의 수명이 무한히 늘어난다
    @Test
    @DisplayName("연장은 남은 시간에 더하지 않고 현재 시각 + TTL로 다시 잡는다")
    void extendUsesAbsoluteExpiry() {
        UserSession session = issueAt(NOW.minus(TTL).plus(RENEW_THRESHOLD));

        session.extend(NOW, TTL);

        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    private static UserSession issueAt(LocalDateTime issuedAt) {
        return UserSession.issue(HASHED_TOKEN, mock(User.class), issuedAt, TTL);
    }
}
