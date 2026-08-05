package com.softeer.race.user.application;

import static com.softeer.race.notification.domain.NotificationType.WELCOME;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_USERNAME;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded-password";
    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationPublisher notificationPublisher;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, notificationPublisher);
    }

    @Test
    @DisplayName("회원가입 시 평문이 아닌 인코딩된 비밀번호를 저장한다")
    void signUpStoresEncodedPassword() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        savesWithId();

        userService.signUp(command);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword())
                .isEqualTo(ENCODED_PASSWORD)
                .isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 중복 이메일 예외를 던진다")
    void signUpRejectsDuplicateEmail() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_EMAIL));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 존재하는 아이디면 중복 아이디 예외를 던진다")
    void signUpRejectsDuplicateUsername() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(userRepository.existsByUsername(command.username())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_USERNAME));

        verify(userRepository, never()).existsByEmail(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("평가사 역할의 자체 회원가입을 거부한다")
    void signUpRejectsEvaluatorRole() {
        SignUpCommand command = signUpCommand(Role.EVALUATOR);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UNSUPPORTED_SIGNUP_ROLE));

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입이 완료되면 저장된 회원에게 환영 알림을 발행한다")
    void signUpPublishesWelcomeNotification() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        savesWithId();

        userService.signUp(command);

        // 환영 알림은 가리킬 대상이 없어서 참조를 싣지 않는다, 이동할 곳은 종류가 고정으로 들고 있다
        verify(notificationPublisher).publish(USER_ID, WELCOME, null);
    }

    @Test
    @DisplayName("가입이 거부되면 환영 알림을 발행하지 않는다")
    void signUpDoesNotPublishWhenRejected() {
        SignUpCommand command = signUpCommand(Role.EVALUATOR);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOf(BusinessException.class);

        verify(notificationPublisher, never()).publish(anyLong(), any(), any());
    }

    @Test
    @DisplayName("저장이 제약 위반으로 실패하면 환영 알림을 발행하지 않는다")
    void signUpDoesNotPublishWhenSaveFails() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Unique index or primary key violation: uk_users_email]"));

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOf(BusinessException.class);

        verify(notificationPublisher, never()).publish(anyLong(), any(), any());
    }

    @Test
    @DisplayName("동시 가입으로 이메일 제약이 위반되면 중복 이메일 예외로 변환한다")
    void signUpConvertsDataIntegrityViolation() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Unique index or primary key violation: uk_users_email]"));

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("동시 가입으로 아이디 제약이 위반되면 중복 아이디 예외로 변환한다")
    void signUpConvertsUsernameDataIntegrityViolation() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Unique index or primary key violation: uk_users_username]"));

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_USERNAME));
    }

    // 실제 저장은 IDENTITY 라 save 시점에 식별자가 붙는다, 발행이 그 값을 쓰므로 대역도 붙여서 돌려준다
    private void savesWithId() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", USER_ID);

            return saved;
        });
    }

    private static SignUpCommand signUpCommand(Role role) {
        return new SignUpCommand(
                "race_kim",
                "race@race.kr",
                RAW_PASSWORD,
                "김레이스",
                "010-1234-5678",
                "서울시 강남구 테헤란로 123",
                role);
    }
}
