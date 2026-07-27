package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.DUPLICATE_EMAIL;
import static com.softeer.race.user.exception.UserErrorCode.UNSUPPORTED_SIGNUP_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.security.PasswordEncoder;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.infrastructure.UserRepository;
import com.softeer.race.user.presentation.dto.request.SignUpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 시 평문이 아닌 인코딩된 비밀번호를 저장한다")
    void signUpStoresEncodedPassword() {
        SignUpRequest request = signUpRequest(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.signUp(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword())
                .isEqualTo(ENCODED_PASSWORD)
                .isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 중복 이메일 예외를 던진다")
    void signUpRejectsDuplicateEmail() {
        SignUpRequest request = signUpRequest(Role.GENERAL);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_EMAIL));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("평가사 역할의 자체 회원가입을 거부한다")
    void signUpRejectsEvaluatorRole() {
        SignUpRequest request = signUpRequest(Role.EVALUATOR);

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UNSUPPORTED_SIGNUP_ROLE));

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 가입으로 저장 시 무결성 위반이 발생하면 중복 이메일 예외로 변환한다")
    void signUpConvertsDataIntegrityViolation() {
        SignUpRequest request = signUpRequest(Role.GENERAL);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(DUPLICATE_EMAIL));
    }

    private static SignUpRequest signUpRequest(Role role) {
        return new SignUpRequest(
                "race@race.kr",
                RAW_PASSWORD,
                "김레이스",
                "010-1234-5678",
                "서울시 강남구 테헤란로 123",
                role);
    }
}
