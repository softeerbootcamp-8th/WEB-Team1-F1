package com.softeer.race.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정. record는 값이 없으면 null로 바인딩되므로 누락된 항목은 기본값으로 메운다.
 * auth 블록이 아예 없어도 기동은 되어야 하고, 잘못된 조합은 기동을 실패시켜야 한다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Session session,
        Cookie cookie,
        /**
         * 허용할 오리진 목록. 정확한 값이 아니라 <b>패턴</b>이며 {@code *} 와일드카드를 쓸 수 있다.
         * 쿠키 인증이라 allowCredentials가 켜져 있는데, Spring은 그 조합에서 allowedOrigins에
         * {@code *}를 넣으면 기동을 실패시키므로 AuthWebMvcConfig가 allowedOriginPatterns로 넘긴다.
         * <p>
         * 비어 있어도 여기서는 통과시키고, 실제로 쓰는 AuthWebMvcConfig가 기동을 실패시킨다.
         * 이 값을 읽지 않는 테스트까지 오리진을 적게 만들지 않으려는 것이다.
         */
        List<String> allowedOrigins
) {

    public AuthProperties {
        session = session != null ? session : new Session(null, null);
        cookie = cookie != null ? cookie : new Cookie(false, null);
        // 기본값으로 메우지 않는다, 무엇을 넣어도 틀린다 — 넓게 잡으면 설정을 지운 쪽이 개방되고
        // 좁게 잡으면 운영에서 조용히 막힌다. 비면 빈 목록으로 두고 AuthWebMvcConfig가 기동을 세운다
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /**
     * 슬라이딩 만료 설정.
     * <p>
     * 남은 시간이 renewThreshold 이하일 때만 만료 시각을 다시 잡으므로, 체감 유휴 타임아웃은 정확히
     * ttl이 아니라 {@code [ttl - renewThreshold, ttl]} 구간이 된다. 기본값이라면 15~30분이다.
     * 정확히 30분 유휴를 보장하려면 ttl 60m / renewThreshold 30m로 잡으면 된다.
     * 대신 쓰기는 세션당 renewThreshold에 한 번으로 묶여 요청 빈도와 무관해진다.
     */
    public record Session(Duration ttl, Duration renewThreshold) {

        private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
        private static final Duration DEFAULT_RENEW_THRESHOLD = Duration.ofMinutes(15);

        public Session {
            ttl = ttl != null ? ttl : DEFAULT_TTL;
            renewThreshold = renewThreshold != null ? renewThreshold : DEFAULT_RENEW_THRESHOLD;
            // 임계값이 ttl 이상이면 발급 직후부터 매 요청이 연장 대상이 되어 최적화가 무의미해진다
            if (ttl.compareTo(renewThreshold) <= 0) {
                throw new IllegalArgumentException(
                        "auth.session.ttl은 auth.session.renew-threshold보다 커야 합니다. ttl=%s, renewThreshold=%s"
                                .formatted(ttl, renewThreshold));
            }
        }
    }

    public record Cookie(boolean secure, String sameSite) {

        private static final String DEFAULT_SAME_SITE = "Lax";

        public Cookie {
            sameSite = sameSite != null ? sameSite : DEFAULT_SAME_SITE;
        }
    }
}
