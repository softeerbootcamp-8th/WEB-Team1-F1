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
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 세션 로그인을 컨트롤러에서 DB까지
 * <p>
 * 1. 왕복
 * 로그인으로 받은 쿠키가 실제로 인증에 쓰이고, 로그아웃 후에는 같은 쿠키가 거부된다
 * <p>
 * 2. 저장 형태
 * 쿠키에는 원문, DB PK에는 SHA-256 해시
 * <p>
 * 3. 만료
 * 상태 플래그가 아니라 expires_at 과 서버 Clock 으로 판정
 * <p>
 * 4. 슬라이딩 갱신
 * 남은 시간이 임계값 이하일 때만 만료 시각이 다시 잡힌다
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
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0, 0);
    private static final Duration TTL = Duration.ofMinutes(30);

    private static final long USER_ID = 81L;
    private static final String USERNAME = "auth_kim";
    private static final String PASSWORD = "password123";

    // 고정 시각은 전진할 수 없으므로 슬라이딩은 시간을 움직이는 대신 expires_at 을 조작해 같은 상태를 만든다
    @BeforeEach
    void fixClock() {
        fixClockAt(NOW);
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

        // then 4 : row 도 남지 않는다
        assertThat(sessionCountOf(sessionCookie.getValue())).isZero();
    }

    // DB가 유출돼도 그것만으로는 세션을 탈취할 수 없어야 한다
    @Test
    @DisplayName("시나리오 2 : 쿠키에는 원문이 담기고 DB PK에는 그 해시가 저장된다")
    void scenario2_SessionTokenIsStoredAsHash() throws Exception {
        // when : 로그인
        Cookie sessionCookie = login();

        // then 1 : 쿠키 값을 그대로 PK로 쓰는 row는 없다
        Integer storedAsRaw = jdbcTemplate.queryForObject(
                "select count(*) from user_session where id = ?", Integer.class, sessionCookie.getValue());
        assertThat(storedAsRaw).isZero();

        // then 2 : 해시로는 정확히 한 건 찾힌다
        assertThat(sessionCountOf(sessionCookie.getValue())).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 3 : 만료 시각이 지난 세션은 AUTH_SESSION_EXPIRED로 거부된다")
    void scenario3_ExpiredSession() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("expired-raw-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("시나리오 4 : 남은 시간이 임계값 이하인 세션은 조회 후 만료 시각이 현재 + TTL로 갱신된다")
    void scenario4_SlidingExpiryRenewsWithinThreshold() throws Exception {
        // given : 남은 시간 10분 (임계값 15분 이하)
        assertThat(expiresAtOf("renewable-raw-token")).isEqualTo(NOW.plusMinutes(10));

        // when
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("renewable-raw-token")))
                .andExpect(status().isOk());

        // then : 인터셉터의 트랜잭션이 핸들러 실행 전에 커밋되므로 갱신이 DB에 남는다
        assertThat(expiresAtOf("renewable-raw-token")).isEqualTo(NOW.plus(TTL));
    }

    // 2초 폴링에 매 요청 UPDATE 가 나가면 한 row 에 쓰기가 집중된다
    @Test
    @DisplayName("시나리오 5 : 남은 시간이 임계값보다 많은 세션은 조회해도 만료 시각이 그대로다")
    void scenario5_SlidingExpiryDoesNotRenewBeyondThreshold() throws Exception {
        // given : 남은 시간 25분 (임계값 15분 초과)
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie("fresh-raw-token")))
                .andExpect(status().isOk());

        // then
        assertThat(expiresAtOf("fresh-raw-token")).isEqualTo(NOW.plusMinutes(25));
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

    // ================= 조회 ====================
    // 애플리케이션이 만드는 SHA-256 hex 와 MySQL 의 sha2 가 같은 값이어야 픽스처의 토큰도 성립한다
    private Integer sessionCountOf(String rawToken) {
        return jdbcTemplate.queryForObject(
                "select count(*) from user_session where id = sha2(?, 256)", Integer.class, rawToken);
    }

    private LocalDateTime expiresAtOf(String rawToken) {
        return jdbcTemplate.queryForObject(
                "select expires_at from user_session where id = sha2(?, 256)",
                LocalDateTime.class, rawToken);
    }
}
