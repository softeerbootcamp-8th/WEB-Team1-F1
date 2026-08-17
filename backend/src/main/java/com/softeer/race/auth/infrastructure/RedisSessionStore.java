package com.softeer.race.auth.infrastructure;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.user.domain.Role;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 세션을 Redis 문자열 키 하나로 저장한다. 만료는 키에 건 TTL이 담당하므로 정리 작업이 없다.
 * <p>
 * 키와 값의 형태를 아는 유일한 지점이다.
 * <pre>
 * session:{세션 토큰} -> "{userId}:{ROLE}"      예) session:kfd9s...Qw -> "42:DEALER"
 * user-sessions:{userId} -> Set&lt;세션 토큰&gt;      예) user-sessions:42 -> {kfd9s...Qw, p1x8...Zt}
 * </pre>
 * JSON이나 Hash가 아니라 구분자 하나짜리 문자열인 이유는 필드가 둘뿐이기 때문이다. Hash로 두면
 * 읽기가 HGETALL, 수명 조회가 별도 명령이 되는데 얻는 것이 없다.
 * <p>
 * <b>역할은 로그인 시점의 스냅샷이다.</b> 인증 경로가 DB를 타지 않는 대신, 세션이 살아 있는 동안
 * 회원의 역할이 바뀌어도 반영되지 않는다. 그래서 역인덱스({@code user-sessions:})를 함께 들고,
 * 역할을 바꾼 쪽이 {@link #deleteAllOf}로 그 회원의 세션을 끊는다.
 * <p>
 * <b>역인덱스에는 만료된 토큰이 남는다.</b> 세션 키는 TTL로 사라지지만 Set의 멤버는 저절로 빠지지
 * 않기 때문이다. 그래서 <b>Set 자체에도 같은 TTL을 걸고</b> 저장·연장 때마다 다시 건다 — 색인은
 * 그 회원의 가장 최근 세션만큼만 살고, 그 안에 남은 죽은 토큰은 폐기할 때 없는 키를 지우는
 * 헛일 한 번으로 끝난다. 로그아웃에서 SREM을 하지 않는 것도 같은 이유다. 그러려면 토큰만 아는
 * 로그아웃 경로가 값을 한 번 더 읽어 userId를 알아내야 하는데, 남겨 둬도 무해한 값이다.
 */
@Component
@RequiredArgsConstructor
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "session:";
    private static final String USER_INDEX_PREFIX = "user-sessions:";
    private static final String FIELD_DELIMITER = ":";

    private final StringRedisTemplate redisTemplate;

    /**
     * 세션과 역인덱스를 한 트랜잭션으로 쓴다.
     * <p>
     * 나눠 보내면 세션은 저장됐는데 색인에 못 들어간 세션이 생길 수 있고, <b>그 세션은
     * {@link #deleteAllOf}에 걸리지 않는다</b> — 역인덱스를 만든 이유가 정확히 그 상황을 없애는
     * 것이라, 여기서만은 왕복을 아끼는 것보다 원자성이 앞선다.
     */
    @Override
    public void save(String token, AuthenticatedUser authenticatedUser, Duration ttl) {
        String value = serialize(authenticatedUser);
        String userIndexKey = userIndexKey(authenticatedUser.id());

        redisTemplate.execute(new SessionCallback<Void>() {
            @Override
            public Void execute(RedisOperations operations) {
                operations.multi();
                operations.opsForValue().set(key(token), value, ttl);
                operations.opsForSet().add(userIndexKey, token);
                // 색인에도 수명을 건다. 걸지 않으면 이 키만 영원히 남아 로그인할 때마다 커진다
                operations.expire(userIndexKey, ttl);
                operations.exec();

                return null;
            }
        });
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

    /**
     * 색인의 수명도 함께 늘린다. 세션만 연장하면 오래 머무는 사용자의 색인이 먼저 만료돼,
     * 살아 있는 세션이 폐기 대상에서 빠진다.
     */
    @Override
    public void extend(String token, long userId, Duration ttl) {
        redisTemplate.expire(key(token), ttl);
        redisTemplate.expire(userIndexKey(userId), ttl);
    }

    /**
     * 색인에서 토큰을 빼지 않는다. 그러려면 토큰만 아는 이 경로가 값을 한 번 더 읽어 userId를
     * 알아내야 하는데, 남은 멤버는 색인이 만료될 때 함께 사라지고 그때까지도 무해하다.
     */
    @Override
    public void delete(String token) {
        redisTemplate.delete(key(token));
    }

    /**
     * 색인에 담긴 토큰을 모두 지운다. 이미 만료돼 사라진 키가 섞여 있어도 그만큼 헛일일 뿐이다.
     * <p>
     * 색인 자체도 함께 지운다. 남겨 두면 방금 끊긴 토큰들이 색인 TTL이 다할 때까지 남아,
     * 그 사이 새로 로그인한 세션과 뒤섞인다.
     */
    @Override
    public void deleteAllOf(long userId) {
        String userIndexKey = userIndexKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(userIndexKey);

        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(RedisSessionStore::key).toList());
        }
        redisTemplate.delete(userIndexKey);
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }

    private static String userIndexKey(long userId) {
        return USER_INDEX_PREFIX + userId;
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
