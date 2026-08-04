package com.softeer.race.auth.presentation.support;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

/**
 * 쿠키의 세션 토큰을 검증해 요청 속성에 주체를 심는다.
 * <p>
 * 서블릿 Filter가 아니라 인터셉터인 이유는 응답 포맷이다. DispatcherServlet은 preHandle을 try 안에서
 * 호출하고 여기서 던진 예외를 ExceptionHandlerExceptionResolver로 넘기므로, 인증 실패도
 * GlobalExceptionHandler를 거쳐 다른 API와 같은 ProblemDetail 응답이 된다.
 * 필터는 DispatcherServlet 바깥이라 401 본문을 직접 조립해야 하고 응답 포맷이 이중 관리된다.
 * <p>
 * 리포지토리를 직접 주입하지 않고 SessionService를 경유한다. preHandle은 트랜잭션 밖이므로
 * 트랜잭션 경계를 구조로 강제해 두는 것이다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String LOGIN_USER = AuthInterceptor.class.getName() + ".LOGIN_USER";

    private final SessionService sessionService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {

        // AbstractHandlerMapping은 preflight일 때 핸들러만 PreFlightHandler로 바꾸고 인터셉터 체인은
        // 그대로 유지한다. 걸러 주지 않으면 OPTIONS가 401이 되고, 브라우저는 실제 요청을 보내지 않는다
        // preflight에는 쿠키가 실리지 않으므로 인증을 요구하는 것 자체가 의미가 없다
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        // DispatcherServlet.doDispatch 는 applyPreHandle 을 디스패치마다 조건 없이 부른다. 그래서 비동기
        // 응답이 끝나 돌아오는 ASYNC 디스패치에서도 인증이 다시 돌고, 삼십 분 열려 있는 구독은 그 사이
        // 세션이 만료될 수 있어 이미 다 내려보낸 응답 위에서 인증 예외가 난다
        // 인증은 요청당 한 번 도는 관심사다. 같은 관심사를 필터로 구현하면 OncePerRequestFilter 가
        // shouldNotFilterAsyncDispatch 기본값 true 로 ASYNC 를 건너뛴다, 여기서는 그 기본값을 직접 맞춘다
        // ASYNC 만 건넌다. FORWARD·INCLUDE·ERROR 까지 넓히면 근거 없이 인증을 우회하는 통로가 는다
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        // getCookies()는 쿠키가 하나도 없으면 빈 배열이 아니라 null을 반환한다
        Cookie cookie = WebUtils.getCookie(request, SessionCookieFactory.COOKIE_NAME);

        // 응답을 직접 쓰지 않는다, 실패는 항상 예외로 던져 응답 조립을 한 곳에 남긴다
        AuthenticatedUser authenticatedUser =
                sessionService.authenticate(cookie == null ? null : cookie.getValue());

        request.setAttribute(LOGIN_USER, authenticatedUser);
        return true;
    }
}
