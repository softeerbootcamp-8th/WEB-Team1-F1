package com.softeer.race.auth.application;

import static com.softeer.race.auth.exception.AuthErrorCode.ACCOUNT_SUSPENDED;
import static com.softeer.race.auth.exception.AuthErrorCode.INVALID_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.auth.application.dto.info.LoginInfo;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 세션 발급")
class LoginSessionIssuerTest {

    private static final long USER_ID = 7L;
    private static final String SESSION_TOKEN = "raw-session-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    private LoginSessionIssuer loginSessionIssuer;

    @BeforeEach
    void setUp() {
        loginSessionIssuer = new LoginSessionIssuer(userRepository, sessionService);
    }

    @Test
    @DisplayName("잠근 최신 회원이 이용 중이면 세션을 발급한다")
    void issuesSessionForActiveUser() {
        User user = user();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(sessionService.issue(user)).thenReturn(SESSION_TOKEN);

        LoginInfo info = loginSessionIssuer.issue(USER_ID);

        assertThat(info.sessionToken()).isEqualTo(SESSION_TOKEN);
        assertThat(info.user()).isEqualTo(AuthUserInfo.from(user));
    }

    @Test
    @DisplayName("비밀번호 검증 중 정지됐더라도 잠근 최신 상태로 세션 발급을 거절한다")
    void rejectsUserSuspendedAfterCredentialCheck() {
        User user = user();
        user.suspend("운영 정책 위반");
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginSessionIssuer.issue(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ACCOUNT_SUSPENDED));

        verify(sessionService, never()).issue(user);
    }

    @Test
    @DisplayName("비밀번호 검증 뒤 사라진 회원도 자격 오류로 처리한다")
    void rejectsDeletedUserWithoutRevealingAccountState() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginSessionIssuer.issue(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(INVALID_CREDENTIALS));
    }

    private static User user() {
        User user = User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", Role.GENERAL);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
