package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private static final String RAW_TOKEN = "raw-session-token";
    private static final String HASHED_TOKEN = "hashed-session-token";
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

    // 저장소에는 해시만 남는다는 결정의 회귀 방지선, 원문이 저장되면 저장소 유출로 전원 세션이 탈취된다
    @Test
    @DisplayName("발급하면 원문 토큰을 반환하고 저장되는 키는 해시다")
    void issueStoresHashedTokenAndReturnsRawToken() {
        when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        String issued = sessionService.issue(user());

        assertThat(issued).isEqualTo(RAW_TOKEN);
        verify(sessionStore).save(HASHED_TOKEN, AUTHENTICATED_USER, TTL);
        verify(sessionStore, never()).save(RAW_TOKEN, AUTHENTICATED_USER, TTL);
    }

    @Test
    @DisplayName("유효한 세션이면 세션에 담긴 주체를 반환한다")
    void authenticateReturnsSessionOwner() {
        givenStoredSession(TTL);

        AuthenticatedUser authenticatedUser = sessionService.authenticate(RAW_TOKEN);

        assertThat(authenticatedUser).isEqualTo(AUTHENTICATED_USER);
    }

    // 2초 폴링에 매 요청 쓰기가 나가면 한 키에 쓰기가 집중된다, 성능 결정의 회귀 방지선
    @Test
    @DisplayName("남은 시간이 임계값보다 많으면 수명을 다시 잡지 않는다")
    void authenticateDoesNotExtendWhenRemainingTimeExceedsThreshold() {
        givenStoredSession(RENEW_THRESHOLD.plusSeconds(1));

        sessionService.authenticate(RAW_TOKEN);

        verify(sessionStore, never()).extend(any(), any());
    }

    // 경계를 연장 쪽으로 두지 않으면 임계값에 정확히 걸린 세션이 한 순간 갱신 대상에서 빠진다
    @Test
    @DisplayName("남은 시간이 임계값과 정확히 같으면 수명을 다시 잡는다")
    void authenticateExtendsAtExactThreshold() {
        givenStoredSession(RENEW_THRESHOLD);

        sessionService.authenticate(RAW_TOKEN);

        verify(sessionStore).extend(HASHED_TOKEN, TTL);
    }

    // 남은 시간에 더하는 방식이면 자주 접속한 세션의 수명이 무한히 늘어난다
    @Test
    @DisplayName("연장은 남은 시간에 더하지 않고 TTL로 다시 잡는다")
    void authenticateExtendsWithAbsoluteTtl() {
        givenStoredSession(Duration.ofMinutes(10));

        sessionService.authenticate(RAW_TOKEN);

        verify(sessionStore).extend(HASHED_TOKEN, TTL);
    }

    // 만료된 세션은 저장소가 스스로 지워 없는 세션과 구분되지 않는다
    @Test
    @DisplayName("저장소에 없는 토큰이면 미인증 예외를 던진다")
    void authenticateRejectsUnknownToken() {
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionStore.find(HASHED_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.authenticate(RAW_TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));
    }

    // 쿠키 없는 요청마다 해싱과 조회를 하면 비인증 트래픽이 그대로 저장소 부하가 된다
    @Test
    @DisplayName("토큰이 비어 있으면 조회 없이 미인증 예외를 던진다")
    void authenticateRejectsBlankTokenWithoutLookup() {
        assertThatThrownBy(() -> sessionService.authenticate("  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));

        verify(sessionTokenGenerator, never()).hash(any());
        verify(sessionStore, never()).find(any());
    }

    @Test
    @DisplayName("폐기는 해시로 삭제한다")
    void revokeDeletesByHashedToken() {
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        sessionService.revoke(RAW_TOKEN);

        verify(sessionStore).delete(HASHED_TOKEN);
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
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(sessionStore.find(HASHED_TOKEN)).thenReturn(Optional.of(AUTHENTICATED_USER));
        when(sessionStore.timeToLive(HASHED_TOKEN)).thenReturn(remaining);
    }
}
