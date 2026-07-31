package com.softeer.race.auth.domain;

/**
 * 인증을 통과한 요청 주체. 원시 long이 아니라 타입으로 감싸는 이유는 두 가지다.
 * 인자 리졸버가 애너테이션과 타입 두 조건을 함께 요구해 다른 long 파라미터를 실수로 가로채지 않고,
 * 나중에 필드를 늘려도 호출부 시그니처가 바뀌지 않는다.
 * <p>
 * Role은 담지 않는다. 지금은 인가 검사가 없고, 권한을 세션에 비정규화하면 권한 변경 시 stale이 된다.
 */
public record AuthenticatedUser(long id) {
}
