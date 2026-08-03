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
     * 지금은 인증이 필요한 경로만 화이트리스트로 나열한다. AuctionRoomController가 아직 X-User-Id
     * 임시 헤더를 쓰고 있어 deny-by-default로 두면 기존 흐름과 통합 테스트가 함께 깨진다.
     * <p>
     * TODO X-User-Id를 걷어낼 때 /api/** 를 기본 차단으로 두고 공개 경로만 excludePathPatterns로 뒤집는다.
     * <p>
     * 로그아웃은 넣지 않는다. 이미 만료된 세션의 로그아웃이 401이 되면 멱등성이 깨진다.
     * <p>
     * 인증이 필요한 경로는 여기와 핸들러의 {@code @LoginUser} 파라미터를 <b>둘 다</b> 갖춰야 한다.
     * 여기서만 빠지면 LoginUserArgumentResolver가 401을 던져 안전하게 실패하기는 하지만,
     * 쿠키를 정상적으로 보낸 요청까지 401이 되어 원인을 오해하기 쉽다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/auth/me",
                        "/api/sell",
                        "/api/images/presigned",
                        "/api/vehicles/*/images",
                        // 알림은 전 경로가 본인 것만 다룬다, 세그먼트 수가 달라 ** 로 묶는다
                        "/api/notifications/**",
                        // 세그먼트 하나만 매치하는 * 다. ** 로 넓히면 /api/auctions/{id} 까지 걸려
                        // X-User-Id 를 쓰는 경매방 조회가 401 이 된다
                        "/api/auctions/*/bids");
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
     * TODO 오리진을 확정할 수 있게 되면 application.yml의 auth.allowed-origins를 실제 목록으로 좁힌다.
     * 이 메서드는 그대로 두면 되고, 정확한 오리진 문자열도 패턴으로 그대로 동작한다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(authProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(PREFLIGHT_MAX_AGE_SECONDS);
    }
}
