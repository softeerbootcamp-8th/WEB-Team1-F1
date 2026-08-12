package com.softeer.race.auth.domain;

import com.softeer.race.user.domain.Role;

/**
 * 인증을 통과한 요청 주체. 원시 long이 아니라 타입으로 감싸는 이유는 두 가지다.
 * 인자 리졸버가 애너테이션과 타입 두 조건을 함께 요구해 다른 long 파라미터를 실수로 가로채지 않고,
 * 나중에 필드를 늘려도 호출부 시그니처가 바뀌지 않는다.
 * <p>
 * 역할은 세션에 복사해 두지 않고 인증할 때 회원에서 읽는다. 그래서 세션이 살아 있는 동안 역할이
 * 바뀌어도 다음 요청부터 즉시 반영된다.
 */
public record AuthenticatedUser(long id, Role role) {
}
