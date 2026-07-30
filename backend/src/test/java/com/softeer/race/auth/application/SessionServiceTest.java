package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.SESSION_EXPIRED;
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
import com.softeer.race.auth.domain.SessionTokenGenerator;
import com.softeer.race.auth.domain.UserSession;
import com.softeer.race.auth.domain.UserSessionRepository;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 12, 0, 0);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration RENEW_THRESHOLD = Duration.ofMinutes(15);

    private static final String RAW_TOKEN = "raw-session-token";
    private static final String HASHED_TOKEN = "hashed-session-token";
    private static final long USER_ID = 7L;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private SessionTokenGenerator sessionTokenGenerator;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                userSessionRepository,
                sessionTokenGenerator,
                new AuthProperties(new AuthProperties.Session(TTL, RENEW_THRESHOLD), null, null),
                Clock.fixed(NOW.atZone(KST).toInstant(), KST));
    }

    // DB에는 해시만 남는다는 결정의 회귀 방지선, 원문이 저장되면 DB 유출로 전원 세션이 탈취된다
    @Test
    @DisplayName("발급하면 원문 토큰을 반환하고 저장되는 세션의 PK는 해시다")
    void issueStoresHashedTokenAndReturnsRawToken() {
        User user = mock(User.class);
        when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        String issued = sessionService.issue(user);

        assertThat(issued).isEqualTo(RAW_TOKEN);

        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getId())
                .isEqualTo(HASHED_TOKEN)
                .isNotEqualTo(RAW_TOKEN);
        assertThat(sessionCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    @DisplayName("유효한 세션이면 세션이 가리키는 회원을 반환한다")
    void authenticateReturnsSessionOwner() {
        givenStoredSession(sessionIssuedAt(NOW.minusMinutes(1)));

        AuthenticatedUser authenticatedUser = sessionService.authenticate(RAW_TOKEN);

        assertThat(authenticatedUser).isEqualTo(new AuthenticatedUser(USER_ID));
    }

    // 2초 폴링에 매 요청 UPDATE가 나가면 한 row에 쓰기가 집중된다, 성능 결정의 회귀 방지선
    @Test
    @DisplayName("남은 시간이 임계값보다 많으면 만료 시각을 갱신하지 않는다")
    void authenticateDoesNotExtendWhenRemainingTimeExceedsThreshold() {
        UserSession session = sessionIssuedAt(NOW.minusMinutes(5));
        givenStoredSession(session);

        sessionService.authenticate(RAW_TOKEN);

        // 남은 시간 25분 > 임계값 15분
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plusMinutes(25));
    }

    @Test
    @DisplayName("남은 시간이 임계값 이하면 만료 시각을 현재 시각 + TTL로 갱신한다")
    void authenticateExtendsWhenRemainingTimeWithinThreshold() {
        UserSession session = sessionIssuedAt(NOW.minusMinutes(20));
        givenStoredSession(session);

        sessionService.authenticate(RAW_TOKEN);

        // 남은 시간 10분 <= 임계값 15분
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    @DisplayName("만료된 세션이면 세션 만료 예외를 던진다")
    void authenticateRejectsExpiredSession() {
        UserSession session = UserSession.issue(HASHED_TOKEN, mock(User.class), NOW.minus(TTL), TTL);
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(userSessionRepository.findById(HASHED_TOKEN)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.authenticate(RAW_TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(SESSION_EXPIRED));
    }

    @Test
    @DisplayName("존재하지 않는 토큰이면 미인증 예외를 던진다")
    void authenticateRejectsUnknownToken() {
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(userSessionRepository.findById(HASHED_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.authenticate(RAW_TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));
    }

    // 쿠키 없는 요청마다 해싱과 조회를 하면 비인증 트래픽이 그대로 DB 부하가 된다
    @Test
    @DisplayName("토큰이 비어 있으면 조회 없이 미인증 예외를 던진다")
    void authenticateRejectsBlankTokenWithoutLookup() {
        assertThatThrownBy(() -> sessionService.authenticate("  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));

        verify(sessionTokenGenerator, never()).hash(any());
        verify(userSessionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("폐기는 해시로 삭제한다")
    void revokeDeletesByHashedToken() {
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);

        sessionService.revoke(RAW_TOKEN);

        verify(userSessionRepository).deleteById(HASHED_TOKEN);
    }

    @Test
    @DisplayName("쿠키가 없는 로그아웃도 예외 없이 통과한다")
    void revokeIsIdempotentWithoutToken() {
        sessionService.revoke(null);

        verify(userSessionRepository, never()).deleteById(any());
    }

    private UserSession sessionIssuedAt(LocalDateTime issuedAt) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return UserSession.issue(HASHED_TOKEN, user, issuedAt, TTL);
    }

    private void givenStoredSession(UserSession session) {
        when(sessionTokenGenerator.hash(RAW_TOKEN)).thenReturn(HASHED_TOKEN);
        when(userSessionRepository.findById(HASHED_TOKEN)).thenReturn(Optional.of(session));
    }
}
