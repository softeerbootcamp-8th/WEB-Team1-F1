package com.softeer.race.support.seed;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.user.domain.Role;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Optional;

/**
 * 토큰만 정하면 심어지는 로그인 세션
 * <p>
 * 세션은 테이블이 아니라 Redis 에 살기 때문에 SQL 픽스처로 심을 수 없다. 만료도 TestClock 이 아니라
 * Redis 의 TTL 로 판정되므로 <b>시계를 돌려서는 세션을 만료시킬 수 없다</b> — 만료 시나리오는
 * {@link #expire(String)} 로 만든다. Redis 는 만료된 키를 스스로 지우므로 지운 상태가 곧 만료된 상태다.
 * <p>
 * 심는 것도 읽는 것도 운영 코드와 같은 경로를 타므로 픽스처가 키 형식을 따라 적지 않는다.
 */
@RequiredArgsConstructor
public class SessionSeeder {

    private final SessionStore sessionStore;
    private final Duration defaultTtl;

    /** 로그인 직후와 같은 상태. 남은 수명이 갱신 임계보다 넉넉해 조회가 세션을 건드리지 않는다 */
    public void seed(String token, long userId, Role role) {
        seed(token, userId, role, defaultTtl);
    }

    /** 남은 수명을 직접 정한다. 슬라이딩 갱신의 임계 판정을 보는 시나리오에서 쓴다 */
    public void seed(String token, long userId, Role role, Duration ttl) {
        sessionStore.save(token, new AuthenticatedUser(userId, role), ttl);
    }

    public void expire(String token) {
        sessionStore.delete(token);
    }

    public Optional<AuthenticatedUser> find(String token) {
        return sessionStore.find(token);
    }

    public Duration timeToLive(String token) {
        return sessionStore.timeToLive(token);
    }
}
