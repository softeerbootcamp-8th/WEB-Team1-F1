package com.softeer.race.user.domain;

/**
 * 회원의 서비스 이용 상태. 역할({@link Role})과는 직교한다 — 정지된 딜러는 여전히 딜러이고,
 * 해제되면 원래 역할 그대로 돌아온다. 한 칸에 합치면 해제할 때 무엇으로 되돌릴지 알 수 없다.
 * <p>
 * <b>정지된 회원에게는 살아 있는 세션이 없다</b>는 것이 이 값을 다루는 전제다. 정지가 그 회원의
 * 세션을 전부 끊고({@code UserSuspensionService.suspend}) 로그인도 막으므로
 * ({@code AuthService.login}), 인증을 통과한 요청의 주체는 언제나 {@code ACTIVE}다.
 * 그래서 세션에는 이 값을 싣지 않는다({@code AuthenticatedUser}).
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
