package com.softeer.race.auth.config;

import com.softeer.race.auth.presentation.support.AuthInterceptor;
import com.softeer.race.auth.presentation.support.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
// RaceApplication의 @ConfigurationPropertiesScan과 중복이지만 @WebMvcTest 슬라이스는 그 스캔을
// 걸러내면서 WebMvcConfigurer인 이 클래스는 포함한다, 여기서 활성화해야 슬라이스에서도 주입된다
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

    private final AuthInterceptor authInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AuthProperties authProperties;

    /**
     * API 전체를 인터셉터에 걸고, 인증이 필요한지는 AuthInterceptor가 핸들러를 보고 판정한다.
     * <b>여기에는 경로를 나열하지 않는다.</b> 인증 요구를 선언하는 곳은 핸들러의
     * {@code @LoginUser} 파라미터 한 곳뿐이므로, 새 API가 생겨도 이 메서드는 고치지 않는다.
     * <p>
     * 경로 목록을 걷어낸 이유는 메서드를 구분하지 못했기 때문이다. {@code /api/auctions} 하나에
     * 공개인 목록 조회(GET)와 로그인이 필요한 경매 등록(POST)이 함께 매핑돼 있어, 경로를 넣으면
     * 비회원 조회가 401이 되고 빼면 등록이 무인증으로 뚫렸다.
     * <p>
     * 로그아웃은 {@code @LoginUser}를 받지 않으므로 자연히 공개로 남는다. 이미 만료된 세션의
     * 로그아웃이 401이 되면 멱등성이 깨지므로 그대로 두어야 한다.
     * <p>
     * 선언을 빠뜨린 핸들러가 조용히 공개로 동작하는 문제는 남아 있다. 공개 API를 명시하게 만들고
     * 미선언을 기동에서 실패시키는 일은 별도 이슈로 다룬다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    /**
     * 쿠키 인증이라 allowCredentials(true)가 필수인데, 이 값과 allowedOrigins("*")를 함께 쓰면
     * Spring이 기동을 실패시킨다. 응답에 Access-Control-Allow-Origin: * 를 내리면 브라우저가
     * 자격 증명이 실린 응답을 거부하기 때문이다.
     * <p>
     * 그래서 allowedOrigins가 아니라 allowedOriginPatterns로 넘긴다. 패턴이 맞으면 Spring이
     * 와일드카드가 아니라 <b>요청의 Origin을 그대로 되돌려주므로</b> 자격 증명과 공존한다.
     * <p>
     * 목록이 비면 기동을 세운다. 빈 배열을 그대로 넘기면 CorsRegistration이 생성자에서 잡아 둔
     * {@code allowedOrigins = *}가 살아남고, CorsConfiguration은 allowedOrigins를 먼저 보므로
     * <b>설정을 지운 쪽이 오히려 전면 개방된다.</b> 설정 누락은 기동 실패로 드러나야 한다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = authProperties.allowedOrigins();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "auth.allowed-origins에 허용할 오리진 패턴을 최소 하나 지정해야 합니다. "
                            + "비워 두면 CORS가 전면 개방됩니다.");
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(PREFLIGHT_MAX_AGE_SECONDS);
    }
}
