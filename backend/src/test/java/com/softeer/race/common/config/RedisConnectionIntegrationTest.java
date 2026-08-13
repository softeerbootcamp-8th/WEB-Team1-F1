package com.softeer.race.common.config;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 연결 설정이 실제로 서 있는지 확인
 * <p>
 * 아직 Redis 를 쓰는 기능이 없어, 설정이 어긋나도 알려 주는 곳이 여기밖에 없다.
 * 첫 사용처가 붙을 때까지 이 테스트가 그 자리를 대신한다.
 * <p>
 * 검증 대상은 두 가지다
 * 1. 값이 오가는가 — 붙은 곳이 개발자 노트북의 Redis 가 아니라 이 스위트가 띄운 컨테이너인가
 * 2. TTL 이 도는가 — 세션이든 캐시든 만료를 Redis 에 맡길 것이라 이게 서버 기능으로 살아 있어야 한다
 */
@DisplayName("Redis 연결 통합 테스트")
class RedisConnectionIntegrationTest extends IntegrationTestSupport {

    // 상수
    private static final String KEY = "race:connection-probe";
    private static final String VALUE = "살아 있다";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("시나리오 1 : 값을 넣고 읽으면 넣은 값이 그대로 나온다")
    void scenario1_RoundTrip() {
        // when : 한 건 저장
        redisTemplate.opsForValue().set(KEY, VALUE);

        // then : 같은 값이 그대로 돌아온다, 한글이 깨지지 않는다
        assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(VALUE);
    }

    @Test
    @DisplayName("시나리오 2 : 만료 시간을 걸고 저장하면 그 시간이 키에 남는다")
    void scenario2_TtlIsApplied() {
        // when : 만료 시간을 함께 걸어 저장
        redisTemplate.opsForValue().set(KEY, VALUE, TTL);

        // then : 남은 시간이 건 값 이하이면서 0 보다 크다
        // 서버가 이미 초를 깎기 시작했으므로 정확히 같기를 요구하지 않는다
        Long remaining = redisTemplate.getExpire(KEY);
        assertThat(remaining).isNotNull()
                .isGreaterThan(0)
                .isLessThanOrEqualTo(TTL.getSeconds());
    }
}
