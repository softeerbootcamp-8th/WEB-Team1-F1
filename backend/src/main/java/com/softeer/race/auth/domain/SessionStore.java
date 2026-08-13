package com.softeer.race.auth.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * 발급된 세션의 저장소. 키는 원문 토큰이 아니라 그 해시이고, 저장소가 유출돼도 원문을 되돌릴 수 없다.
 * <p>
 * 만료는 저장소가 TTL로 직접 관리한다. 만료 시각을 값으로 들고 다니며 비교하지 않으므로
 * 만료된 세션을 지우는 정리 작업도 필요 없다. 대신 <b>"만료됐다"와 "없다"를 구분할 수 없다</b> —
 * 저장소가 만료된 키를 스스로 지우기 때문이고, 두 경우 모두 조회가 비어 돌아온다.
 * <p>
 * 연장 정책(임계값 판정)은 여기가 아니라 SessionService가 가진다. 이 인터페이스는 값을 읽고,
 * 남은 수명을 알려주고, 수명을 다시 잡는 세 가지 원시 연산만 제공한다.
 */
public interface SessionStore {

    /** 만료까지의 시간을 함께 건다. ttl이 지나면 저장소가 알아서 회수한다. */
    void save(String hashedToken, AuthenticatedUser authenticatedUser, Duration ttl);

    /** 없거나 이미 만료됐으면 비어 있다. */
    Optional<AuthenticatedUser> find(String hashedToken);

    /** 남은 수명. 키가 없으면 {@link Duration#ZERO}다. */
    Duration timeToLive(String hashedToken);

    /** 남은 시간에 더하지 않고 지금부터 ttl로 다시 잡는다. 없는 키에 대해서는 아무 일도 하지 않는다. */
    void extend(String hashedToken, Duration ttl);

    /** 없는 키여도 예외를 던지지 않는다. 로그아웃은 멱등해야 한다. */
    void delete(String hashedToken);
}
