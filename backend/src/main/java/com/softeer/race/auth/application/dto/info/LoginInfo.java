package com.softeer.race.auth.application.dto.info;

/**
 * 로그인 결과. 토큰과 회원 정보를 한 단계 분리해 두면 응답 매핑이 user()만 바라보게 되어
 * 세션 토큰이 응답 본문에 실릴 수 없다.
 */
public record LoginInfo(String sessionToken, AuthUserInfo user) {
}
