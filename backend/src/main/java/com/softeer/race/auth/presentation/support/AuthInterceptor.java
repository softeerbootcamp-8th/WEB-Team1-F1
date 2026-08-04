package com.softeer.race.auth.presentation.support;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.Arrays;

/**
 * 쿠키의 세션 토큰을 검증해 요청 속성에 주체를 심는다.
 * <p>
 * <b>인증이 필요한지는 경로가 아니라 핸들러가 결정한다.</b> 핸들러 파라미터에 {@code @LoginUser}가
 * 있으면 인증을 요구하고, 없으면 공개로 통과시킨다. 경로 패턴은 메서드를 구분하지 못해
 * {@code GET /api/auctions}(공개 목록)와 {@code POST /api/auctions}(경매 등록)에 서로 다른 조건을
 * 줄 수 없었다. DispatcherServlet이 경로와 메서드 매칭을 끝낸 뒤 preHandle을 부르므로,
 * 판정을 핸들러로 옮기면 메서드별 분기가 따라온다.
 * <p>
 * 판정 신호를 {@code @LoginUser} 하나로 둔 것은 적을 곳을 한 곳으로 줄이기 위함이다. 대신
 * <b>선언을 빠뜨린 핸들러는 조용히 공개로 동작한다.</b> 공개 API가 아무 표시를 하지 않는 것이
 * 정상이라 누락과 구분할 방법이 없다. 기본 차단으로 뒤집는 일은 별도 이슈로 다룬다.
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

        // 정적 리소스처럼 핸들러 메서드가 아닌 요청은 판정할 파라미터가 없다
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!requiresAuthentication(handlerMethod)) {
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

    /**
     * 이 저장소의 컨트롤러는 Swagger 애너테이션을 {@code *Api} 인터페이스에, Spring MVC 애너테이션을
     * 구현체에 둔다. HandlerMethod는 브리지된 구현 메서드를 보므로 인터페이스에 선언한 파라미터
     * 애너테이션은 여기서 보이지 않는다. {@code @LoginUser}는 반드시 구현체에 붙여야 한다.
     */
    private boolean requiresAuthentication(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(LoginUser.class));
    }
}
