package com.softeer.race.notification.presentation;

import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.seed.SessionFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 실시간 구독 엔드포인트의 인증 계약
 * <p>
 * <b>스트림 내용은 여기서 검증하지 않는다.</b> 열린 응답에 이어 쓰는 흐름은 MockMvc 로 재현되지 않아
 * {@code NotificationPushIntegrationTest} 가 채널 단위로 확인한다. 여기서 잡는 것은 요청이 들어오는
 * 지점의 두 가지다.
 * <p>
 * <b>성공 시나리오가 {@code @LoginUser} 의 위치를 증명한다.</b> AuthInterceptor 는 구현 메서드의
 * 파라미터 애너테이션으로 인증 요구를 판정하는데, 그것을 Api 인터페이스에만 선언하면 인터셉터가
 * 주체를 심지 않고 인자 리졸버가 401 을 던진다. 즉 <b>쿠키를 정상적으로 보낸 요청이 실패한다.</b>
 * 실패 시나리오만으로는 둘을 구분할 수 없다(어느 쪽이든 401 이다).
 * <p>
 * <b>실패 시나리오는 응답 형식을 함께 본다.</b> 핸들러가 {@code text/event-stream} 만 생산한다고
 * 선언돼 있어서, 오류 응답이 그 제약에 걸려 406 이 되지 않는지 확인한다.
 */
@DisplayName("알림 실시간 구독 인증 통합 테스트")
@Sql("/sql/notification-fixture.sql")
class NotificationStreamIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/api/notifications/stream";
    private static final String MY_TOKEN = "notification-my-token";

    // 픽스처의 세션 만료(2026-08-03 13:00)보다 앞이어야 하고, 갱신 임계(15분)에 걸리지 않을 만큼 남아야 한다
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @BeforeEach
    void fixTime() {
        fixClockAt(FIXED_NOW);
    }


    // 세션만 Redis 에 살아 @Sql 이 함께 심지 못한다, 짝이 되는 세션을 여기서 심는다
    @BeforeEach
    void seedSessions() {
        SessionFixture.notification(sessions);
    }

    @Test
    @DisplayName("시나리오 1 : 세션 쿠키로 스트림이 열린다")
    void scenario1_OpensStreamForLoggedInUser() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, MY_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        // 열어 둔 구독은 컨텍스트에 남으므로 끝내 준다, 해제 콜백이 채널에서 빼 간다
        result.getRequest().getAsyncContext().complete();
    }

    @Test
    @DisplayName("시나리오 2 : 세션 쿠키가 없으면 401 이고, 오류 응답 형식이 스트림 제약에 막히지 않는다")
    void scenario2_RejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
