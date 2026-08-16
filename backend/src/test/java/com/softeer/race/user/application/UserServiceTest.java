package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_PHONE;
import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_USERNAME;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.dealer.application.DealerApplicationService;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import java.time.LocalDateTime;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.user.exception.UserErrorCode;
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
    private DealerApplicationService dealerApplicationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, dealerApplicationService);
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
    @DisplayName("이미 존재하는 휴대전화 번호면 중복 번호 예외를 던진다")
    void signUpRejectsDuplicatePhone() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(userRepository.existsByPhone(command.phone())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_PHONE));

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

    // 관리자는 부트스트랩으로만 심는다. 여기가 뚫리면 가입 폼으로 아무나 관리자가 된다
    @Test
    @DisplayName("관리자 역할의 자체 회원가입을 거부한다")
    void signUpRejectsAdminRole() {
        SignUpCommand command = signUpCommand(Role.ADMIN);

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UNSUPPORTED_SIGNUP_ROLE));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("일반 회원가입에 사원증 키가 오면 거부한다")
    void signUpRejectsGeneralWithLicense() {
        SignUpCommand command = signUpCommand(Role.GENERAL, dealerLicenseKey());

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.DEALER_LICENSE_NOT_ALLOWED));

        verify(dealerApplicationService, never()).apply(any(), any());
        verify(userRepository, never()).save(any());
    }

    // 이 한 줄이 이번 변경의 핵심이다. 예전에는 아무도 검토하지 않은 사원증으로 딜러가 됐다
    @Test
    @DisplayName("딜러로 신청해도 회원은 일반 회원으로 만든다")
    void signUpCreatesGeneralUserForDealerApplicant() {
        SignUpCommand command = signUpCommand(Role.DEALER, dealerLicenseKey());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        savesWithId();
        appliesWithStatus(DealerApplicationStatus.PENDING);

        SignUpInfo info = userService.signUp(command);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.GENERAL);
        assertThat(info.role()).isEqualTo(Role.GENERAL);
    }

    @Test
    @DisplayName("딜러로 신청하면 저장된 회원으로 심사 신청을 접수한다")
    void signUpAppliesDealerApplication() {
        SignUpCommand command = signUpCommand(Role.DEALER, dealerLicenseKey());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        savesWithId();
        appliesWithStatus(DealerApplicationStatus.PENDING);

        SignUpInfo info = userService.signUp(command);

        verify(dealerApplicationService).apply(USER_ID, dealerLicenseKey());
        // 응답에 담기지 않으면 클라이언트는 딜러 선택이 접수된 것인지 무시된 것인지 알 수 없다
        assertThat(info.dealerApplicationStatus()).isEqualTo(DealerApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("일반 회원가입은 심사 신청을 만들지 않는다")
    void signUpDoesNotApplyForGeneralRole() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        savesWithId();

        SignUpInfo info = userService.signUp(command);

        verify(dealerApplicationService, never()).apply(any(), any());
        assertThat(info.dealerApplicationStatus()).isNull();
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

    @Test
    @DisplayName("동시 가입으로 휴대전화 제약이 위반되면 중복 번호 예외로 변환한다")
    void signUpConvertsPhoneDataIntegrityViolation() {
        SignUpCommand command = signUpCommand(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry '01012345678' for key 'uk_users_phone'"));

        assertThatThrownBy(() -> userService.signUp(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_PHONE));
    }

    private void appliesWithStatus(DealerApplicationStatus status) {
        when(dealerApplicationService.apply(any(), any()))
                .thenReturn(new DealerApplicationInfo(10L, status, null, LocalDateTime.now()));
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
        return signUpCommand(role, null);
    }

    private static SignUpCommand signUpCommand(Role role, String dealerLicenseKey) {
        return new SignUpCommand(
                "race_kim",
                "race@race.kr",
                RAW_PASSWORD,
                "김레이스",
                "01012345678",
                role,
                dealerLicenseKey);
    }

    private static String dealerLicenseKey() {
        return "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    }
}
