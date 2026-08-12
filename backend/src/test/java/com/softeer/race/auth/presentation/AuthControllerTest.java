package com.softeer.race.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.AuthService;
import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.application.dto.command.LoginCommand;
import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.user.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = AuthController.class)
// SessionCookieFactory는 슬라이스 스캔 대상이 아닌 일반 @Component라 직접 넣어 준다
// 쿠키 속성이 이 테스트의 검증 대상이므로 목으로 대체하지 않는다
@Import({GlobalExceptionHandler.class, SessionCookieFactory.class})
class AuthControllerTest {

    private static final String SESSION_TOKEN = "raw-session-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // AuthInterceptor가 슬라이스에 함께 스캔되므로 그 의존성을 채워 준다
    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("로그인 성공은 회원 정보만 본문에 담고 세션 토큰은 본문에 노출하지 않는다")
    void login() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenReturn(loginInfo());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("race_kim"))
                .andExpect(jsonPath("$.email").value("race@race.kr"))
                .andExpect(jsonPath("$.realName").value("김레이스"))
                .andExpect(jsonPath("$.role").value("GENERAL"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(SESSION_TOKEN);
    }

    @Test
    @DisplayName("로그인 응답 쿠키는 HttpOnly · Path=/ · SameSite=Lax이고 Secure와 Max-Age는 없다")
    void loginSetsSessionCookie() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenReturn(loginInfo());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(SessionCookieFactory.COOKIE_NAME + "=" + SESSION_TOKEN)
                .contains("HttpOnly")
                .contains("Path=/")
                .contains("SameSite=Lax")
                // 로컬은 http라 Secure를 켜면 브라우저가 쿠키를 저장하지 않는다
                .doesNotContain("Secure")
                // 만료의 권위는 DB의 expires_at 하나여야 한다
                .doesNotContain("Max-Age");
    }

    @Test
    @DisplayName("자격 오류는 AUTH_INVALID_CREDENTIALS로 401을 반환한다")
    void loginFailure() throws Exception {
        when(authService.login(any(LoginCommand.class)))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("빈 아이디와 빈 비밀번호는 필드별 오류와 함께 400을 반환한다")
    void loginValidationFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("username", "password")));
    }

    @Test
    @DisplayName("로그아웃은 204와 함께 Max-Age=0 쿠키를 내려 기존 쿠키를 지운다")
    void logout() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, SESSION_TOKEN)))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(authService).logout(SESSION_TOKEN);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(SessionCookieFactory.COOKIE_NAME + "=")
                .contains("Max-Age=0")
                .contains("Path=/");
    }

    // 만료된 세션의 로그아웃이 401이 되면 프론트가 로그아웃할 방법이 없어진다
    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204다")
    void logoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("세션 쿠키가 있으면 내 정보를 반환한다")
    void me() throws Exception {
        when(sessionService.authenticate(SESSION_TOKEN))
                .thenReturn(new AuthenticatedUser(1L, Role.GENERAL));
        when(authService.me(1L)).thenReturn(authUserInfo());

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, SESSION_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("race_kim"));
    }

    // 인터셉터가 던진 예외가 GlobalExceptionHandler를 거쳐 다른 API와 같은 형식으로 나오는지 고정한다
    // 필터로 구현했다면 이 경로가 성립하지 않는다
    @Test
    @DisplayName("쿠키 없이 내 정보를 조회하면 인터셉터가 AUTH_UNAUTHENTICATED로 401을 반환한다")
    void meWithoutCookie() throws Exception {
        when(sessionService.authenticate(any()))
                .thenThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("로그인이 필요합니다."));
    }

    private static LoginInfo loginInfo() {
        return new LoginInfo(SESSION_TOKEN, authUserInfo());
    }

    private static AuthUserInfo authUserInfo() {
        return new AuthUserInfo(1L, "race_kim", "race@race.kr", "김레이스", Role.GENERAL);
    }

    private static String loginRequest() {
        return """
                {
                  "username": "race_kim",
                  "password": "password123"
                }
                """;
    }
}
