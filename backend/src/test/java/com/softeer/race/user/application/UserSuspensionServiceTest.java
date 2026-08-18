package com.softeer.race.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.application.dto.command.SuspendUserCommand;
import com.softeer.race.user.application.dto.info.UserStatusInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.user.domain.UserStatus;
import com.softeer.race.user.exception.UserErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 회원 이용정지")
class UserSuspensionServiceTest {

    private static final long USER_ID = 42L;
    private static final String REASON = "허위 매물을 반복 등록했습니다.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    private UserSuspensionService userSuspensionService;

    @BeforeEach
    void setUp() {
        userSuspensionService = new UserSuspensionService(userRepository, sessionService);
    }

    @Test
    @DisplayName("정지하면 사유가 남고 역할은 그대로다")
    void suspendKeepsRole() {
        given(user(Role.DEALER));

        UserStatusInfo info = userSuspensionService.suspend(new SuspendUserCommand(USER_ID, REASON));

        assertThat(info.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(info.suspendReason()).isEqualTo(REASON);
        assertThat(info.role()).isEqualTo(Role.DEALER);
    }

    /*
     * 끊지 않으면 정지가 지금 접속 중인 사람에게만 듣지 않는다. 인증이 로그인 시점에 복사된 세션
     * 하나로 끝나므로 그 회원은 최대 세션 TTL 만큼 정지 전 그대로 이용한다
     */
    @Test
    @DisplayName("정지는 그 회원의 세션을 함께 끊는다")
    void suspendRevokesSessions() {
        given(user(Role.GENERAL));

        userSuspensionService.suspend(new SuspendUserCommand(USER_ID, REASON));

        verify(sessionService).revokeAllOf(USER_ID);
    }

    @Test
    @DisplayName("해제하면 사유가 지워지고 다시 이용할 수 있다")
    void activateClearsReason() {
        User user = user(Role.GENERAL);
        user.suspend(REASON);
        given(user);

        UserStatusInfo info = userSuspensionService.activate(USER_ID);

        assertThat(info.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(info.suspendReason()).isNull();
    }

    // 정지된 회원에게는 살아 있는 세션이 없다. 끊을 것이 없어서 부르지 않는다
    @Test
    @DisplayName("해제는 세션을 건드리지 않는다")
    void activateKeepsSessions() {
        User user = user(Role.GENERAL);
        user.suspend(REASON);
        given(user);

        userSuspensionService.activate(USER_ID);

        verify(sessionService, never()).revokeAllOf(USER_ID);
    }

    // 관리자를 막는 것이 곧 자기 자신을 정지할 수 없다는 보장이다
    @Test
    @DisplayName("관리자는 정지할 수 없고 세션도 끊기지 않는다")
    void suspendRejectsAdmin() {
        given(user(Role.ADMIN));

        assertThatThrownBy(() ->
                userSuspensionService.suspend(new SuspendUserCommand(USER_ID, REASON)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.NOT_SUSPENDABLE_ROLE));

        verify(sessionService, never()).revokeAllOf(USER_ID);
    }

    @Test
    @DisplayName("이미 정지된 회원은 다시 정지할 수 없다")
    void suspendRejectsSuspendedUser() {
        User user = user(Role.GENERAL);
        user.suspend("먼저 남은 사유");
        given(user);

        assertThatThrownBy(() ->
                userSuspensionService.suspend(new SuspendUserCommand(USER_ID, REASON)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.ALREADY_SUSPENDED));

        // 앞선 사유가 덮이지 않아야 한다
        assertThat(user.getSuspendReason()).isEqualTo("먼저 남은 사유");
    }

    @Test
    @DisplayName("정지되지 않은 회원은 해제할 수 없다")
    void activateRejectsActiveUser() {
        given(user(Role.GENERAL));

        assertThatThrownBy(() -> userSuspensionService.activate(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("없는 회원을 정지하려 하면 404다")
    void suspendRejectsMissingUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userSuspensionService.suspend(new SuspendUserCommand(USER_ID, REASON)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.NOT_FOUND));
    }

    private void given(User user) {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    // 실제 저장은 IDENTITY 라 save 시점에 식별자가 붙는다, 세션 폐기가 그 값을 쓰므로 대역에도 넣는다
    private static User user(Role role) {
        User user = User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", role);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }
}
