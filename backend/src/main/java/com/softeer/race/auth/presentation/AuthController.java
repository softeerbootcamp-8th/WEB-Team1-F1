package com.softeer.race.auth.presentation;

import com.softeer.race.auth.application.AuthService;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.request.LoginRequest;
import com.softeer.race.auth.presentation.response.AuthUserResponse;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;

    /**
     * 세션 토큰은 Set-Cookie로만 나가고 응답 본문에는 넣지 않는다.
     * 본문에 실으면 프론트가 localStorage에 담을 여지가 생겨 HttpOnly가 무의미해진다.
     */
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 세션을 발급하고 HttpOnly 쿠키로 내려줍니다.")
    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginInfo info = authService.login(request.toCommand());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.create(info.sessionToken()).toString())
                .body(AuthUserResponse.from(info.user()));
    }

    /** 쿠키가 없거나 이미 만료된 세션이어도 204다. 로그아웃은 멱등해야 한다. */
    @Operation(summary = "로그아웃", description = "세션을 폐기하고 쿠키를 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String sessionToken) {

        authService.logout(sessionToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieFactory.expire().toString())
                .build();
    }

    @Operation(summary = "내 정보 조회", description = "세션 쿠키로 인증된 회원 정보를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(@LoginUser AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(AuthUserResponse.from(authService.me(authenticatedUser.id())));
    }
}
