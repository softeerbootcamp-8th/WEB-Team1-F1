package com.softeer.race.auth.presentation.support;

import com.softeer.race.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 세션 쿠키의 발급과 삭제.
 * <p>
 * 삭제 쿠키는 발급 쿠키와 name · path · domain이 모두 같아야 브라우저가 기존 쿠키를 대체한다.
 * 하나만 어긋나도 조용히 지워지지 않으므로 두 경로를 한 클래스에서 같은 빌더로 만든다.
 * <p>
 * jakarta.servlet.http.Cookie에는 SameSite API가 없어 헤더를 직접 조립해야 하므로
 * spring-web의 ResponseCookie를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SessionCookieFactory {

    // 컨테이너가 관리하는 JSESSIONID와 이름이 겹치면 어느 쪽이 우리 세션인지 구분하기 어려워진다
    // @CookieValue의 name으로 쓸 수 있도록 컴파일 상수로 둔다
    public static final String COOKIE_NAME = "RACE_SESSION";

    private static final String PATH = "/";

    private final AuthProperties authProperties;

    public ResponseCookie create(String token) {
        return builder(token).build();
    }

    public ResponseCookie expire() {
        return builder("").maxAge(0).build();
    }

    /**
     * Max-Age를 두지 않아 브라우저 종료 시 사라지는 세션 쿠키가 된다.
     * 만료의 권위는 세션 저장소에 걸린 TTL 하나여야 하고, 쿠키에도 수명을 두면 슬라이딩 연장마다
     * Set-Cookie를 다시 내려 동기화해야 한다. 서버가 만료로 판정하면 401이 나가고 프론트가 처리한다.
     * Domain도 두지 않아 host-only 쿠키가 된다.
     */
    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                // 로컬은 http라 설정으로 받는다, Secure를 켜면 브라우저가 쿠키를 저장하지 않는다
                .secure(authProperties.cookie().secure())
                .path(PATH)
                .sameSite(authProperties.cookie().sameSite());
    }
}
