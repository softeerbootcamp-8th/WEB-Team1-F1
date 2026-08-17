package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.ACCOUNT_SUSPENDED;
import static com.softeer.race.auth.exception.AuthErrorCode.INVALID_CREDENTIALS;
import static com.softeer.race.auth.exception.AuthErrorCode.UNAUTHENTICATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.auth.application.dto.command.LoginCommand;
import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.exception.ErrorCode;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USERNAME = "race_kim";
    private static final String EMAIL = "race@race.kr";
    private static final String REAL_NAME = "김레이스";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded-password";
    private static final String SESSION_TOKEN = "raw-session-token";
    private static final long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, sessionService);
    }

    @Test
    @DisplayName("로그인에 성공하면 세션 토큰과 회원 정보를 함께 반환한다")
    void login() {
        User user = loginableUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(sessionService.issue(user)).thenReturn(SESSION_TOKEN);

        LoginInfo info = authService.login(new LoginCommand(USERNAME, RAW_PASSWORD));

        assertThat(info.sessionToken()).isEqualTo(SESSION_TOKEN);
        assertThat(info.user())
                .isEqualTo(new AuthUserInfo(USER_ID, USERNAME, EMAIL, REAL_NAME, Role.GENERAL));
    }

    // 두 실패의 코드가 갈리면 응답 자체가 계정 존재 여부를 알려주는 오라클이 된다
    @Test
    @DisplayName("없는 아이디와 틀린 비밀번호는 구별할 수 없는 같은 오류 코드를 낸다")
    void loginDoesNotDistinguishUnknownUsernameFromWrongPassword() {
        User user = credentialUser();
        when(userRepository.findByUsername("nobody00")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", ENCODED_PASSWORD)).thenReturn(false);

        ErrorCode unknownUsername =
                errorCodeOf(() -> authService.login(new LoginCommand("nobody00", "wrongpass")));
        ErrorCode wrongPassword =
                errorCodeOf(() -> authService.login(new LoginCommand(USERNAME, "wrongpass")));

        assertThat(unknownUsername).isEqualTo(wrongPassword).isEqualTo(INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("없는 아이디로는 비밀번호 검증도 세션 발급도 하지 않는다")
    void loginWithUnknownUsernameDoesNotIssueSession() {
        when(userRepository.findByUsername("nobody00")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("nobody00", RAW_PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_CREDENTIALS));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(sessionService, never()).issue(any());
    }

    // 로그인 요청에는 가입 경로의 ASCII 제약이 없어 64자 한글(192바이트)이 들어올 수 있다
    // 그대로 bcrypt에 넘기면 IllegalArgumentException이 나 500이 된다
    @Test
    @DisplayName("bcrypt 한계를 넘는 바이트 길이의 비밀번호는 검증을 시도하지 않고 자격 오류로 막는다")
    void loginRejectsPasswordExceedingBcryptByteLimit() {
        User user = credentialUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginCommand(USERNAME, "가".repeat(64))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_CREDENTIALS));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(sessionService, never()).issue(any());
    }

    // 세션 폐기는 지금 접속 중인 사람을 끊고, 이 검사는 다시 들어오는 문을 막는다
    // 둘이 함께여야 "정지된 회원에게는 살아 있는 세션이 없다"가 성립한다
    @Test
    @DisplayName("이용이 정지된 계정은 자격이 맞아도 로그인할 수 없다")
    void loginRejectsSuspendedAccount() {
        User user = credentialUser();
        when(user.isSuspended()).thenReturn(true);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand(USERNAME, RAW_PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ACCOUNT_SUSPENDED));

        verify(sessionService, never()).issue(any());
    }

    /*
     * 검사 순서가 곧 계정 열거 방어다. 정지 여부를 비밀번호 검증보다 앞에 두면 비밀번호를 모르는
     * 사람도 아이디만으로 그 계정이 정지 상태인지 알아낼 수 있어, 없는 아이디와 틀린 비밀번호를
     * 한 코드로 합쳐 막아 둔 오라클이 그대로 다시 열린다
     */
    @Test
    @DisplayName("정지된 계정이어도 비밀번호가 틀리면 정지 사실을 알려주지 않는다")
    void loginDoesNotRevealSuspensionBeforeVerifyingPassword() {
        User user = credentialUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand(USERNAME, "wrongpass")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_CREDENTIALS));

        verify(user, never()).isSuspended();
    }

    @Test
    @DisplayName("내 정보 조회는 비밀번호를 담지 않은 회원 정보를 반환한다")
    void me() {
        User user = profileUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AuthUserInfo info = authService.me(USER_ID);

        assertThat(info).isEqualTo(new AuthUserInfo(USER_ID, USERNAME, EMAIL, REAL_NAME, Role.GENERAL));
    }

    // 세션은 살아 있는데 회원이 사라진 상태를 없는 리소스가 아니라 인증 실패로 다룬다
    @Test
    @DisplayName("세션이 가리키는 회원이 없으면 미인증 예외를 던진다")
    void meRejectsMissingUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UNAUTHENTICATED));
    }

    @Test
    @DisplayName("로그아웃은 세션 폐기로 위임된다")
    void logout() {
        authService.logout(SESSION_TOKEN);

        verify(sessionService).revoke(SESSION_TOKEN);
    }

    private static ErrorCode errorCodeOf(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).errorCode();
    }

    /** 비밀번호 검증까지만 가는 경로 */
    private static User credentialUser() {
        User user = mock(User.class);
        when(user.getPassword()).thenReturn(ENCODED_PASSWORD);
        return user;
    }

    /** 정보 변환만 하는 경로 */
    private static User profileUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn(USERNAME);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getRealName()).thenReturn(REAL_NAME);
        when(user.getRole()).thenReturn(Role.GENERAL);
        return user;
    }

    /** 검증과 정보 변환을 모두 거치는 경로 */
    private static User loginableUser() {
        User user = profileUser();
        when(user.getPassword()).thenReturn(ENCODED_PASSWORD);
        return user;
    }
}
