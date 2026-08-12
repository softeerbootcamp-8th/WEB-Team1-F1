package com.softeer.race.auth.presentation.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.annotation.RequireRole;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증이 필요한지를 경로가 아니라 핸들러가 결정한다는 계약을 고정한다.
 * <p>
 * 실제 컨트롤러 대신 테스트 전용 컨트롤러를 쓴다. 검증 대상이 "같은 경로에서 GET은 공개, POST는
 * 인증"이라는 판정 규칙 자체라, 어느 도메인의 컨트롤러를 골라도 그 도메인 사정에 흔들리기 때문이다.
 * 실제 배선은 AuctionControllerTest가 {@code /api/auctions}로 확인한다.
 * <p>
 * {@code @WebMvcTest} 슬라이스는 WebMvcConfigurer를 함께 스캔하므로 AuthWebMvcConfig의
 * {@code /api/**} 등록이 이 컨텍스트에서도 살아 있다.
 */
@WebMvcTest(controllers = AuthInterceptorTest.TestController.class)
@Import({AuthInterceptorTest.TestController.class, GlobalExceptionHandler.class})
@DisplayName("인증 인터셉터")
class AuthInterceptorTest {

    private static final String RAW_TOKEN = "interceptor-raw-token";
    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("@LoginUser가 없는 핸들러는 쿠키 없이도 통과하고 세션을 조회하지 않는다")
    void publicHandlerSkipsAuthentication() throws Exception {
        mockMvc.perform(get("/api/interceptor-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("public"));

        // 통과만 확인하면 인증을 걸었는데 예외를 삼키는 구현도 통과한다, 조회 자체가 없어야 한다
        verify(sessionService, never()).authenticate(any());
    }

    @Test
    @DisplayName("@LoginUser가 있는 핸들러는 같은 경로여도 쿠키가 없으면 401이다")
    void protectedHandlerRejectsMissingCookie() throws Exception {
        given(sessionService.authenticate(null))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        mockMvc.perform(post("/api/interceptor-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("@LoginUser가 있는 핸들러는 쿠키를 보내면 주체가 주입된다")
    void protectedHandlerInjectsPrincipal() throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(USER_ID, Role.GENERAL));

        mockMvc.perform(post("/api/interceptor-test")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    @DisplayName("@RequireRole만 있는 핸들러도 인증을 요구한다")
    void roleProtectedHandlerRequiresAuthentication() throws Exception {
        given(sessionService.authenticate(null))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        mockMvc.perform(post("/api/interceptor-test/evaluator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("허용되지 않은 역할은 403으로 거부한다")
    void roleProtectedHandlerRejectsDisallowedRole() throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(USER_ID, Role.GENERAL));

        mockMvc.perform(post("/api/interceptor-test/evaluator")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("허용된 역할은 @LoginUser 파라미터 없이도 통과한다")
    void roleProtectedHandlerAllowsRole() throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(USER_ID, Role.EVALUATOR));

        mockMvc.perform(post("/api/interceptor-test/evaluator")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("evaluator"));
    }

    @Test
    @DisplayName("여러 허용 역할 중 하나만 일치해도 통과한다")
    void roleProtectedHandlerAllowsAnyDeclaredRole() throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(USER_ID, Role.DEALER));

        mockMvc.perform(post("/api/interceptor-test/trader")
                        .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN)))
                .andExpect(status().isOk());
    }

    // 인터셉터 범위가 /api/** 로 넓어졌으므로 preflight가 401이 되는 회귀를 여기서 잡는다
    // preflight에는 쿠키가 실리지 않아, 걸러 주지 않으면 브라우저가 실제 요청을 보내지 않는다
    @Test
    @DisplayName("인증이 필요한 핸들러의 preflight는 쿠키가 없어도 통과한다")
    void preflightPasses() throws Exception {
        mockMvc.perform(options("/api/interceptor-test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk());

        verify(sessionService, never()).authenticate(any());
    }

    @RestController
    @RequestMapping("/api/interceptor-test")
    static class TestController {

        @GetMapping
        PublicResult open() {
            return new PublicResult("public");
        }

        @PostMapping
        PrincipalResult secured(@LoginUser AuthenticatedUser authenticatedUser) {
            return new PrincipalResult(authenticatedUser.id());
        }

        @PostMapping("/evaluator")
        @RequireRole(Role.EVALUATOR)
        PublicResult evaluatorOnly() {
            return new PublicResult("evaluator");
        }

        @PostMapping("/trader")
        @RequireRole({Role.DEALER, Role.EVALUATOR})
        PublicResult traderOnly() {
            return new PublicResult("trader");
        }
    }

    record PublicResult(String result) {
    }

    record PrincipalResult(long userId) {
    }
}
