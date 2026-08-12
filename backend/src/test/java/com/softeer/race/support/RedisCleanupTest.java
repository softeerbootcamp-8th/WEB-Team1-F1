package com.softeer.race.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// 컨테이너가 스위트에서 하나뿐이라 정리 훅이 빠지면 키가 테스트를 넘어 산다
// 두 테스트가 같은 키를 쓴다, 어느 쪽이 먼저 돌든 나중에 도는 쪽이 앞이 남긴 값을 보면 깨진다
@DisplayName("테스트 간 Redis 격리")
class RedisCleanupTest extends IntegrationTestSupport {

    private static final String KEY = "race:cleanup-probe";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("빈 상태에서 시작해 키를 하나 남긴다")
    void startsCleanAndLeavesKey() {
        startCleanThenSet("leftover1");
    }

    @Test
    @DisplayName("형제 테스트가 남긴 키가 보이지 않는다")
    void doesNotSeeKeyFromSibling() {
        startCleanThenSet("leftover2");
    }

    // 값을 다르게 둔다, 정리가 안 됐을 때 존재 여부가 아니라 어느 쪽이 남았는지까지 드러나야 원인이 보인다
    private void startCleanThenSet(String value) {
        assertThat(redisTemplate.opsForValue().get(KEY)).isNull();

        redisTemplate.opsForValue().set(KEY, value);

        assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo(value);
    }
}
