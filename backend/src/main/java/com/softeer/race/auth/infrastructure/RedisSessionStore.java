package com.softeer.race.auth.infrastructure;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.user.domain.Role;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 세션을 Redis 문자열 키 하나로 저장한다. 만료는 키에 건 TTL이 담당하므로 정리 작업이 없다.
 * <p>
 * 키와 값의 형태를 아는 유일한 지점이다.
 * <pre>
 * session:{세션 토큰} -> "{userId}:{ROLE}"      예) session:kfd9s...Qw -> "42:DEALER"
 * </pre>
 * JSON이나 Hash가 아니라 구분자 하나짜리 문자열인 이유는 필드가 둘뿐이기 때문이다. Hash로 두면
 * 읽기가 HGETALL, 수명 조회가 별도 명령이 되는데 얻는 것이 없다.
 * <p>
 * <b>역할은 로그인 시점의 스냅샷이다.</b> 인증 경로가 DB를 타지 않는 대신, 세션이 살아 있는 동안
 * 회원의 역할이 바뀌어도 최대 TTL만큼 반영되지 않는다. 역할을 바꾸는 기능을 들일 때는
 * 그 회원의 세션을 함께 폐기해야 하고, 그러려면 회원 -> 세션 역인덱스가 필요하다.
 */
@Component
@RequiredArgsConstructor
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "session:";
    private static final String FIELD_DELIMITER = ":";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, AuthenticatedUser authenticatedUser, Duration ttl) {
        redisTemplate.opsForValue().set(key(token), serialize(authenticatedUser), ttl);
    }

    @Override
    public Optional<AuthenticatedUser> find(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(token)))
                .flatMap(RedisSessionStore::deserialize);
    }

    @Override
    public Duration timeToLive(String token) {
        Long seconds = redisTemplate.getExpire(key(token), TimeUnit.SECONDS);
        // 키가 없으면 -2, 수명이 걸려 있지 않으면 -1이다. 저장이 항상 TTL을 걸므로 -1은 나오지 않고,
        // -2는 조회와 이 호출 사이에 만료된 경우다. 남은 시간 0으로 보면 뒤이어 연장을 시도하지만
        // 없는 키의 EXPIRE 는 아무 일도 하지 않으므로 세션이 되살아나지는 않는다
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    @Override
    public void extend(String token, Duration ttl) {
        redisTemplate.expire(key(token), ttl);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(key(token));
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }

    private static String serialize(AuthenticatedUser authenticatedUser) {
        return authenticatedUser.id() + FIELD_DELIMITER + authenticatedUser.role().name();
    }

    /**
     * 읽을 수 없는 값은 예외가 아니라 빈 값으로 흘린다. 역할 이름을 바꾸거나 값 형식을 손본 채로
     * 배포하면 이전 형식의 세션이 TTL이 다할 때까지 남는데, 그걸 500으로 만들 이유가 없다.
     * 세션이 없는 것으로 보면 재로그인으로 자연히 해소된다.
     */
    private static Optional<AuthenticatedUser> deserialize(String value) {
        int delimiterIndex = value.indexOf(FIELD_DELIMITER);
        if (delimiterIndex < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AuthenticatedUser(
                    Long.parseLong(value.substring(0, delimiterIndex)),
                    Role.valueOf(value.substring(delimiterIndex + 1))));
        } catch (IllegalArgumentException exception) {
            // NumberFormatException 도 IllegalArgumentException 이라 함께 걸린다
            return Optional.empty();
        }
    }
}
