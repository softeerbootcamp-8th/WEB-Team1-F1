package com.softeer.race.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import com.softeer.race.user.domain.Role;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 세션 로그인을 컨트롤러에서 저장소까지
 * <p>
 * 1. 왕복
 * 로그인으로 받은 쿠키가 실제로 인증에 쓰이고, 로그아웃 후에는 같은 쿠키가 거부된다
 * <p>
 * 2. 저장 형태
 * 쿠키 값이 그대로 저장소 키가 되고, 값에는 회원의 id와 역할이 담긴다
 * <p>
 * 3. 만료
 * 상태 플래그도 만료 시각 비교도 아니고 저장소의 TTL 로 판정. 만료된 세션은 저장소에서 사라져
 * 없는 세션과 구분되지 않으므로 둘 다 미인증이다
 * <p>
 * 4. 슬라이딩 갱신
 * 남은 수명이 임계값 이하일 때만 다시 잡힌다
 * <p>
 * 5. CORS
 * 인터셉터가 붙은 경로여도 OPTIONS 는 401 로 막히지 않고, 지금은 어떤 오리진도 허용된다
 */
@DisplayName("세션 로그인 통합 테스트")
// 부모가 테스트마다 테이블을 비우므로 픽스처는 시나리오마다 새로 심는다
// 모든 시나리오가 같은 회원과 같은 세션 집합을 쓰므로 픽스처는 하나로 둔다
@Sql("/sql/auth-session-fixture.sql")
class AuthIntegrationTest extends IntegrationTestSupport {

    // 상수
    // application.yml 의 auth.session 과 같은 값이다
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration RENEW_THRESHOLD = Duration.ofMinutes(15);

    private static final long USER_ID = 81L;
    private static final String USERNAME = "auth_kim";
    private static final String PASSWORD = "password123";

    // 시계를 고정하지 않는다. 세션의 수명은 서버 Clock 이 아니라 저장소의 TTL 로 흐른다
    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void seedSessions() {
        SessionFixture.authSession(sessions);
    }

    @Test
    @DisplayName("시나리오 1 : 로그인 -> 내 정보 조회 -> 로그아웃 -> 같은 쿠키는 더 이상 인증되지 않는다")
    void scenario1_LoginToLogoutRoundTrip() throws Exception {
        // given : 로그인으로 세션 쿠키를 받는다
        Cookie sessionCookie = login();

        // then 1 : 브라우저 스크립트가 읽을 수 없어야 한다
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getPath()).isEqualTo("/");

        // then 2 : 받은 쿠키만으로 인증된다
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.password").doesNotExist());

        // when : 로그아웃
        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isNoContent());

        // then 3 : 세션이 사라졌으므로 만료가 아니라 미인증이다
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));

        // then 4 : 저장소에도 남지 않는다
        assertThat(sessions.find(sessionCookie.getValue())).isEmpty();
    }

    // 저장 형태 자체가 검증 대상이라 이 시나리오만 예외적으로 키를 직접 들여다본다
    // 역할이 값에 복사된다는 것이 인증 경로가 회원을 다시 읽지 않는 근거다
    @Test
    @DisplayName("시나리오 2 : 쿠키 값이 그대로 저장소 키가 되고 값에는 회원의 id와 역할이 담긴다")
    void scenario2_SessionIsStoredUnderCookieValue() throws Exception {
        // when : 로그인
        Cookie sessionCookie = login();

        // then
        assertThat(redisTemplate.opsForValue().get("session:" + sessionCookie.getValue()))
                .isEqualTo(USER_ID + ":" + Role.GENERAL);
    }

    // 만료된 세션은 저장소가 스스로 지운다, 그래서 없는 세션과 같은 코드로 거부된다
    @Test
    @DisplayName("시나리오 3 : 만료된 세션은 AUTH_UNAUTHENTICATED로 거부된다")
    void scenario3_ExpiredSession() throws Exception {
        // given : 살아 있던 세션이 만료된다
        sessions.seed("expiring-raw-token", USER_ID, Role.GENERAL);
        sessions.expire("expiring-raw-token");

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("expiring-raw-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("시나리오 4 : 남은 수명이 임계값 이하인 세션은 조회 후 TTL로 다시 잡힌다")
    void scenario4_SlidingExpiryRenewsWithinThreshold() throws Exception {
        // given : 남은 수명 10분 (임계값 15분 이하)
        assertThat(sessions.timeToLive("renewable-raw-token")).isLessThanOrEqualTo(RENEW_THRESHOLD);

        // when
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("renewable-raw-token")))
                .andExpect(status().isOk());

        // then : 남은 수명에 더한 것이 아니라 TTL 로 다시 잡혔다, 10분 + 30분이면 이 단언이 깨진다
        assertThat(sessions.timeToLive("renewable-raw-token"))
                .isGreaterThan(TTL.minusMinutes(1))
                .isLessThanOrEqualTo(TTL);
    }

    // 2초 폴링에 매 요청 쓰기가 나가면 한 키에 쓰기가 집중된다
    @Test
    @DisplayName("시나리오 5 : 남은 수명이 임계값보다 많은 세션은 조회해도 그대로다")
    void scenario5_SlidingExpiryDoesNotRenewBeyondThreshold() throws Exception {
        // given : 남은 수명 25분 (임계값 15분 초과)
        Duration seeded = Duration.ofMinutes(25);

        // when
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("fresh-raw-token")))
                .andExpect(status().isOk());

        // then : 갱신됐다면 30분으로 늘어난다
        assertThat(sessions.timeToLive("fresh-raw-token"))
                .isGreaterThan(seeded.minusMinutes(1))
                .isLessThanOrEqualTo(seeded);
    }

    // 인터셉터가 preflight 를 401 로 막으면 브라우저에서는 실제 요청이 아예 나가지 않는다
    @Test
    @DisplayName("시나리오 6 : 인증이 필요한 경로의 CORS preflight는 401이 아니라 허용된다")
    void scenario6_PreflightIsNotBlockedByInterceptor() throws Exception {
        preflight("http://localhost:5173")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // 목록에 없는 오리진은 preflight 단계에서 끊긴다, 브라우저는 실제 요청을 보내지 않는다
    // allowedOrigins 가 비어 CorsRegistration 의 기본값 * 가 되살아나면 이 테스트가 먼저 깨진다
    @Test
    @DisplayName("시나리오 7 : 목록에 없는 오리진의 preflight는 거부되고 허용 헤더가 나가지 않는다")
    void scenario7_UnknownOriginIsRejected() throws Exception {
        preflight("http://192.168.0.10:5173")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // 백엔드 자신의 오리진(8080)을 목록에서 빼면 이 테스트가 403 으로 깨진다
    // Spring 5.3 부터 CorsUtils.isCorsRequest 는 Origin 헤더의 존재만 보고 요청 URL 과 비교하지 않는데,
    // 브라우저는 same-origin 이라도 GET/HEAD 가 아니면 Origin 을 붙인다
    // 그래서 8080 에 뜬 Swagger UI 에서 GET 은 되고 POST 만 403 이 되는 형태로 나타난다
    @Test
    @DisplayName("시나리오 8 : 백엔드 자신의 오리진에서 온 POST도 CORS 검사를 타므로 목록에 있어야 통과한다")
    void scenario8_SameOriginPostStillNeedsAllowedOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk());
    }

    // ================= 요청 ====================
    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie(SessionCookieFactory.COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private static Cookie sessionCookie(String rawToken) {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, rawToken);
    }

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/auth/me")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET"));
    }

}
