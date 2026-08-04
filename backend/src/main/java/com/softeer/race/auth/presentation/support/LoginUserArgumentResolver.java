package com.softeer.race.auth.presentation.support;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.common.exception.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginUser 파라미터에 인터셉터가 심어 둔 주체를 꽂는다.
 * <p>
 * 리졸버가 직접 세션을 조회하지 않는다. 인증 판정이 인터셉터 한 곳에 모이고, 세션 조회가
 * 요청당 한 번으로 보장된다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    // 애너테이션과 타입을 함께 요구해 다른 파라미터를 실수로 가로채지 않는다
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Object authenticatedUser =
                webRequest.getAttribute(AuthInterceptor.LOGIN_USER, RequestAttributes.SCOPE_REQUEST);

        // 인터셉터는 @LoginUser를 보고 인증을 요구하므로 /api/** 안에서는 여기가 비어 있을 수 없다
        // /api/** 밖에 매핑된 핸들러가 @LoginUser를 붙인 경우를 조용한 NPE가 아니라 401로 드러낸다
        if (authenticatedUser == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHENTICATED);
        }
        return authenticatedUser;
    }
}
