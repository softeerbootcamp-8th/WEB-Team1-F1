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

    private static final String TOKEN = "session-token";
    private static final String KEY = "session:" + TOKEN;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final AuthenticatedUser DEALER = new AuthenticatedUser(42L, Role.DEALER);

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장한 주체가 역할까지 그대로 돌아온다")
    void savesAndFinds() {
        sessionStore.save(TOKEN, DEALER, TTL);

        assertThat(sessionStore.find(TOKEN)).contains(DEALER);
    }

    // 만료를 지우는 작업 없이 저장소가 스스로 회수한다는 결정의 회귀 방지선
    @Test
    @DisplayName("저장에는 수명이 함께 걸린다")
    void savesWithTimeToLive() {
        sessionStore.save(TOKEN, DEALER, TTL);

        assertThat(sessionStore.timeToLive(TOKEN))
                .isGreaterThan(TTL.minusMinutes(1))
                .isLessThanOrEqualTo(TTL);
    }

    @Test
    @DisplayName("없는 세션은 빈 값이고 남은 수명도 0이다")
    void missingSessionHasNoValueAndNoTimeToLive() {
        assertThat(sessionStore.find(TOKEN)).isEmpty();
        assertThat(sessionStore.timeToLive(TOKEN)).isZero();
    }

    // 조회와 연장 사이에 만료되면 없는 키에 연장이 나간다, 그때 빈 세션이 생기면 인증이 뚫린다
    @Test
    @DisplayName("없는 세션에 수명을 다시 잡아도 세션이 되살아나지 않는다")
    void extendDoesNotResurrectMissingSession() {
        sessionStore.extend(TOKEN, DEALER.id(), TTL);

        assertThat(sessionStore.find(TOKEN)).isEmpty();
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("연장은 남은 수명에 더하지 않고 지금부터 다시 잡는다")
    void extendResetsTimeToLive() {
        sessionStore.save(TOKEN, DEALER, Duration.ofMinutes(10));

        sessionStore.extend(TOKEN, DEALER.id(), TTL);

        assertThat(sessionStore.timeToLive(TOKEN))
                .isGreaterThan(TTL.minusMinutes(1))
                .isLessThanOrEqualTo(TTL);
    }

    // 값 형식이나 역할 이름을 바꾼 채 배포하면 이전 형식의 세션이 수명이 다할 때까지 남는다
    @Test
    @DisplayName("읽을 수 없는 값은 예외가 아니라 빈 값이 된다")
    void unreadableValueIsTreatedAsMissing() {
        redisTemplate.opsForValue().set(KEY, "42:CHAIRMAN");

        assertThat(sessionStore.find(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("없는 세션을 지워도 예외를 던지지 않는다")
    void deleteIsIdempotent() {
        assertThatCode(() -> sessionStore.delete(TOKEN)).doesNotThrowAnyException();
    }

    // 역할을 바꾼 뒤 이걸 부르지 않으면 그 회원은 최대 TTL 만큼 바뀌기 전 권한으로 요청할 수 있다
    @Test
    @DisplayName("한 회원의 세션은 기기 수와 무관하게 한 번에 모두 끊긴다")
    void deleteAllOfRevokesEverySession() {
        sessionStore.save("phone-token", DEALER, TTL);
        sessionStore.save("laptop-token", DEALER, TTL);

        sessionStore.deleteAllOf(DEALER.id());

        assertThat(sessionStore.find("phone-token")).isEmpty();
        assertThat(sessionStore.find("laptop-token")).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 세션은 건드리지 않는다")
    void deleteAllOfLeavesOtherUsers() {
        AuthenticatedUser other = new AuthenticatedUser(99L, Role.GENERAL);
        sessionStore.save(TOKEN, DEALER, TTL);
        sessionStore.save("other-token", other, TTL);

        sessionStore.deleteAllOf(DEALER.id());

        assertThat(sessionStore.find("other-token")).contains(other);
    }

    // 세션 키는 TTL 로 사라지지만 Set 멤버는 저절로 빠지지 않아, 색인에는 죽은 토큰이 섞여 있다
    @Test
    @DisplayName("색인에 죽은 토큰이 섞여 있어도 살아 있는 세션은 끊긴다")
    void deleteAllOfIgnoresDeadTokens() {
        sessionStore.save("expired-token", DEALER, TTL);
        // 세션 키만 지워 색인에 토큰만 남은 상태를 만든다 — delete 가 SREM 을 하지 않으므로 실제로 이렇게 된다
        sessionStore.delete("expired-token");
        sessionStore.save("live-token", DEALER, TTL);

        assertThatCode(() -> sessionStore.deleteAllOf(DEALER.id())).doesNotThrowAnyException();
        assertThat(sessionStore.find("live-token")).isEmpty();
    }

    @Test
    @DisplayName("로그인한 적 없는 회원을 폐기해도 예외를 던지지 않는다")
    void deleteAllOfIsIdempotent() {
        assertThatCode(() -> sessionStore.deleteAllOf(DEALER.id())).doesNotThrowAnyException();
    }

    // 색인에 수명이 없으면 로그인할 때마다 커지기만 하고 영원히 남는다
    @Test
    @DisplayName("회원별 색인에도 세션과 같은 수명이 걸린다")
    void userIndexExpiresWithSession() {
        sessionStore.save(TOKEN, DEALER, TTL);

        Long indexTtl = redisTemplate.getExpire("user-sessions:" + DEALER.id());

        assertThat(indexTtl).isPositive().isLessThanOrEqualTo(TTL.toSeconds());
    }

    // 세션만 연장하면 오래 머무는 사용자의 색인이 먼저 만료돼, 살아 있는 세션이 폐기에서 빠진다
    @Test
    @DisplayName("연장은 색인의 수명도 함께 늘린다")
    void extendAlsoExtendsUserIndex() {
        sessionStore.save(TOKEN, DEALER, Duration.ofMinutes(10));

        sessionStore.extend(TOKEN, DEALER.id(), TTL);

        assertThat(redisTemplate.getExpire("user-sessions:" + DEALER.id()))
                .isGreaterThan(Duration.ofMinutes(10).toSeconds());
    }

    // 폐기 뒤 색인을 남겨 두면 방금 끊긴 토큰들이 새로 로그인한 세션과 뒤섞인다
    @Test
    @DisplayName("폐기 후 다시 로그인한 세션은 이전 폐기에 영향받지 않는다")
    void newSessionAfterRevokeSurvives() {
        sessionStore.save("old-token", DEALER, TTL);
        sessionStore.deleteAllOf(DEALER.id());

        sessionStore.save("new-token", DEALER, TTL);

        assertThat(sessionStore.find("new-token")).contains(DEALER);
        assertThat(redisTemplate.opsForSet().members("user-sessions:" + DEALER.id()))
                .containsExactly("new-token");
    }
}
