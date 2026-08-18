package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.ACCOUNT_SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.softeer.race.auth.application.dto.command.LoginCommand;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.auth.domain.SessionStore;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.user.application.UserSuspensionService;
import com.softeer.race.user.application.dto.command.SuspendUserCommand;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.user.domain.UserStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("로그인과 이용정지 동시성")
@Sql("/sql/auth-session-fixture.sql")
class LoginSuspensionConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final long USER_ID = 81L;
    private static final String USERNAME = "auth_kim";
    private static final String PASSWORD = "password123";
    private static final String SUSPEND_REASON = "운영 정책 위반";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserSuspensionService userSuspensionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionStore sessionStore;

    @MockitoSpyBean
    private SessionService sessionService;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    /**
     * 로그인 쪽이 회원 행을 잠근 뒤 세션 저장 직전에서 멈춘다. 정지는 같은 행 잠금을 기다려야 하며,
     * 로그인이 세션을 저장하고 락을 놓은 다음 그 신규 세션까지 폐기해야 한다.
     */
    @Test
    @DisplayName("로그인이 먼저 잠금을 잡아도 정지 완료 뒤에는 신규 세션이 남지 않는다")
    void suspensionRevokesSessionIssuedByConcurrentLogin() throws Exception {
        String existingToken = "existing-session-before-suspension";
        sessions.seed(existingToken, USER_ID, Role.GENERAL);

        CountDownLatch issuingSession = new CountDownLatch(1);
        CountDownLatch allowSessionIssue = new CountDownLatch(1);
        CountDownLatch suspensionStarted = new CountDownLatch(1);
        CountDownLatch revokingSessions = new CountDownLatch(1);

        doAnswer(invocation -> {
            issuingSession.countDown();
            await(allowSessionIssue);
            return invocation.callRealMethod();
        }).when(sessionService).issue(any(User.class));
        doAnswer(invocation -> {
            revokingSessions.countDown();
            return invocation.callRealMethod();
        }).when(sessionService).revokeAllOf(USER_ID);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<LoginInfo> login = pool.submit(() ->
                    authService.login(new LoginCommand(USERNAME, PASSWORD)));
            assertThat(issuingSession.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> suspension = pool.submit(() -> {
                suspensionStarted.countDown();
                return userSuspensionService.suspend(new SuspendUserCommand(USER_ID, SUSPEND_REASON));
            });
            assertThat(suspensionStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // 정지는 로그인 트랜잭션이 쥔 사용자 행 잠금 앞에서 기다린다.
            assertThat(revokingSessions.await(500, TimeUnit.MILLISECONDS)).isFalse();

            allowSessionIssue.countDown();
            LoginInfo loginInfo = login.get(10, TimeUnit.SECONDS);
            suspension.get(10, TimeUnit.SECONDS);

            assertThat(sessionStore.find(loginInfo.sessionToken())).isEmpty();
            assertThat(sessionStore.find(existingToken)).isEmpty();
            assertThat(userRepository.findById(USER_ID).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.SUSPENDED);
        } finally {
            allowSessionIssue.countDown();
        }
    }

    /** 정지가 bcrypt보다 먼저 끝나면 로그인의 잠금 재조회가 최신 상태를 보고 세션을 만들지 않는다. */
    @Test
    @DisplayName("비밀번호 검증 중 정지가 끝나면 로그인은 최신 상태로 거절된다")
    void loginRejectsSuspensionCompletedDuringPasswordVerification() throws Exception {
        CountDownLatch verifyingPassword = new CountDownLatch(1);
        CountDownLatch allowPasswordVerification = new CountDownLatch(1);

        doAnswer(invocation -> {
            verifyingPassword.countDown();
            await(allowPasswordVerification);
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(anyString(), anyString());

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<LoginInfo> login = pool.submit(() ->
                    authService.login(new LoginCommand(USERNAME, PASSWORD)));
            assertThat(verifyingPassword.await(10, TimeUnit.SECONDS)).isTrue();

            userSuspensionService.suspend(new SuspendUserCommand(USER_ID, SUSPEND_REASON));
            allowPasswordVerification.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> login.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause())
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ACCOUNT_SUSPENDED));
            verify(sessionService, never()).issue(any(User.class));
        } finally {
            allowPasswordVerification.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }
}
