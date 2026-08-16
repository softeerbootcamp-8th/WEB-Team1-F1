package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.auth.config.AuthProperties;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.auth.domain.SessionTokenGenerator;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration RENEW_THRESHOLD = Duration.ofMinutes(15);

    private static final String TOKEN = "session-token";
    private static final long USER_ID = 7L;
    private static final AuthenticatedUser AUTHENTICATED_USER =
            new AuthenticatedUser(USER_ID, Role.EVALUATOR);

    @Mock
    private SessionStore sessionStore;

    @Mock
    private SessionTokenGenerator sessionTokenGenerator;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                sessionStore,
                sessionTokenGenerator,
                new AuthProperties(new AuthProperties.Session(TTL, RENEW_THRESHOLD), null, null));
    }

    // 회원의 역할이 발급 시점에 세션으로 복사된다, 인증이 회원을 다시 읽지 않는 근거다
    @Test
    @DisplayName("발급하면 그 토큰을 키로 회원의 id와 역할이 저장된다")
    void issueStoresAuthenticatedUserUnderToken() {
        when(sessionTokenGenerator.generate()).thenReturn(TOKEN);

        String issued = sessionService.issue(user());

        assertThat(issued).isEqualTo(TOKEN);
        verify(sessionStore).save(TOKEN, AUTHENTICATED_USER, TTL);
    }

    @Test
    @DisplayName("유효한 세션이면 세션에 담긴 주체를 반환한다")
    void authenticateReturnsSessionOwner() {
        givenStoredSession(TTL);

        AuthenticatedUser authenticatedUser = sessionService.authenticate(TOKEN);

        assertThat(authenticatedUser).isEqualTo(AUTHENTICATED_USER);
    }

    // 2초 폴링에 매 요청 쓰기가 나가면 한 키에 쓰기가 집중된다, 성능 결정의 회귀 방지선
    @Test
    @DisplayName("남은 시간이 임계값보다 많으면 수명을 다시 잡지 않는다")
    void authenticateDoesNotExtendWhenRemainingTimeExceedsThreshold() {
        givenStoredSession(RENEW_THRESHOLD.plusSeconds(1));

        sessionService.authenticate(TOKEN);

        verify(sessionStore, never()).extend(any(), anyLong(), any());
    }

    // 경계를 연장 쪽으로 두지 않으면 임계값에 정확히 걸린 세션이 한 순간 갱신 대상에서 빠진다
    @Test
    @DisplayName("남은 시간이 임계값과 정확히 같으면 수명을 다시 잡는다")
    void authenticateExtendsAtExactThreshold() {
        givenStoredSession(RENEW_THRESHOLD);

        sessionService.authenticate(TOKEN);

        verify(sessionStore).extend(TOKEN, USER_ID, TTL);
    }

    // 남은 시간에 더하는 방식이면 자주 접속한 세션의 수명이 무한히 늘어난다
    @Test
    @DisplayName("연장은 남은 시간에 더하지 않고 TTL로 다시 잡는다")
    void authenticateExtendsWithAbsoluteTtl() {
        givenStoredSession(Duration.ofMinutes(10));

        sessionService.authenticate(TOKEN);

        verify(sessionStore).extend(TOKEN, USER_ID, TTL);
    }

    // 역할을 바꾼 쪽이 이걸 부르지 않으면 그 회원은 최대 TTL 만큼 바뀌기 전 권한으로 요청할 수 있다
    @Test
    @DisplayName("회원 단위 폐기는 저장소에 그대로 위임한다")
    void revokeAllOfDelegatesToStore() {
        sessionService.revokeAllOf(USER_ID);

        verify(sessionStore).deleteAllOf(USER_ID);
    }

    // 만료된 세션은 저장소가 스스로 지워 없는 세션과 구분되지 않는다
    @Test
    @DisplayName("저장소에 없는 토큰이면 미인증 예외를 던진다")
    void authenticateRejectsUnknownToken() {
        when(sessionStore.find(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.authenticate(TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));
    }

    // 쿠키 없는 요청마다 조회를 하면 비인증 트래픽이 그대로 저장소 부하가 된다
    @Test
    @DisplayName("토큰이 비어 있으면 조회 없이 미인증 예외를 던진다")
    void authenticateRejectsBlankTokenWithoutLookup() {
        assertThatThrownBy(() -> sessionService.authenticate("  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));

        verify(sessionStore, never()).find(any());
    }

    @Test
    @DisplayName("폐기는 토큰으로 삭제한다")
    void revokeDeletesByToken() {
        sessionService.revoke(TOKEN);

        verify(sessionStore).delete(TOKEN);
    }

    @Test
    @DisplayName("쿠키가 없는 로그아웃도 예외 없이 통과한다")
    void revokeIsIdempotentWithoutToken() {
        sessionService.revoke(null);

        verify(sessionStore, never()).delete(any());
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(Role.EVALUATOR);
        return user;
    }

    private void givenStoredSession(Duration remaining) {
        when(sessionStore.find(TOKEN)).thenReturn(Optional.of(AUTHENTICATED_USER));
        when(sessionStore.timeToLive(TOKEN)).thenReturn(remaining);
    }
}
