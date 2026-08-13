package com.softeer.race.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.domain.Role;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 세션 저장소를 Redis 에 붙여서
 * <p>
 * SessionService 는 저장소를 목으로 두므로 여기서만 드러나는 것들을 본다 — 수명이 실제로 걸리는지,
 * 없는 키에 수명을 다시 잡아도 세션이 되살아나지 않는지, 읽을 수 없는 값이 500 이 되지 않는지.
 */
@DisplayName("Redis 세션 저장소 통합 테스트")
class RedisSessionStoreIntegrationTest extends IntegrationTestSupport {

    private static final String HASHED_TOKEN = "0".repeat(64);
    private static final String KEY = "session:" + HASHED_TOKEN;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final AuthenticatedUser DEALER = new AuthenticatedUser(42L, Role.DEALER);

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장한 주체가 역할까지 그대로 돌아온다")
    void savesAndFinds() {
        sessionStore.save(HASHED_TOKEN, DEALER, TTL);

        assertThat(sessionStore.find(HASHED_TOKEN)).contains(DEALER);
    }

    // 만료를 지우는 작업 없이 저장소가 스스로 회수한다는 결정의 회귀 방지선
    @Test
    @DisplayName("저장에는 수명이 함께 걸린다")
    void savesWithTimeToLive() {
        sessionStore.save(HASHED_TOKEN, DEALER, TTL);

        assertThat(sessionStore.timeToLive(HASHED_TOKEN))
                .isGreaterThan(TTL.minusMinutes(1))
                .isLessThanOrEqualTo(TTL);
    }

    @Test
    @DisplayName("없는 세션은 빈 값이고 남은 수명도 0이다")
    void missingSessionHasNoValueAndNoTimeToLive() {
        assertThat(sessionStore.find(HASHED_TOKEN)).isEmpty();
        assertThat(sessionStore.timeToLive(HASHED_TOKEN)).isZero();
    }

    // 조회와 연장 사이에 만료되면 없는 키에 연장이 나간다, 그때 빈 세션이 생기면 인증이 뚫린다
    @Test
    @DisplayName("없는 세션에 수명을 다시 잡아도 세션이 되살아나지 않는다")
    void extendDoesNotResurrectMissingSession() {
        sessionStore.extend(HASHED_TOKEN, TTL);

        assertThat(sessionStore.find(HASHED_TOKEN)).isEmpty();
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("연장은 남은 수명에 더하지 않고 지금부터 다시 잡는다")
    void extendResetsTimeToLive() {
        sessionStore.save(HASHED_TOKEN, DEALER, Duration.ofMinutes(10));

        sessionStore.extend(HASHED_TOKEN, TTL);

        assertThat(sessionStore.timeToLive(HASHED_TOKEN))
                .isGreaterThan(TTL.minusMinutes(1))
                .isLessThanOrEqualTo(TTL);
    }

    // 값 형식이나 역할 이름을 바꾼 채 배포하면 이전 형식의 세션이 수명이 다할 때까지 남는다
    @Test
    @DisplayName("읽을 수 없는 값은 예외가 아니라 빈 값이 된다")
    void unreadableValueIsTreatedAsMissing() {
        redisTemplate.opsForValue().set(KEY, "42:CHAIRMAN");

        assertThat(sessionStore.find(HASHED_TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("없는 세션을 지워도 예외를 던지지 않는다")
    void deleteIsIdempotent() {
        assertThatCode(() -> sessionStore.delete(HASHED_TOKEN)).doesNotThrowAnyException();
    }
}
