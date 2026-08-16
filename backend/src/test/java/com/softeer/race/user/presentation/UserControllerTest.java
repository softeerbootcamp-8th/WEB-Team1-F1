package com.softeer.race.user.presentation;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.user.application.UserService;
import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // @WebMvcTest 슬라이스는 WebMvcConfigurer와 HandlerInterceptor 빈을 함께 스캔한다
    // AuthInterceptor가 들어오면서 그 의존성인 SessionService가 없어 컨텍스트 로딩이 실패하므로 채워 준다
    // /api/users는 인터셉터 경로가 아니라 이 목이 호출되지는 않는다
    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("정상 회원가입 요청은 비밀번호 없이 201 응답을 반환한다")
    void signUp() throws Exception {
        when(userService.signUp(any(SignUpCommand.class)))
                .thenReturn(new SignUpInfo(1L, "race_kim", "race@race.kr", "김레이스", Role.GENERAL, null));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("race_kim"))
                .andExpect(jsonPath("$.email").value("race@race.kr"))
                .andExpect(jsonPath("$.realName").value("김레이스"))
                .andExpect(jsonPath("$.role").value("GENERAL"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("딜러 회원가입의 사원증 키는 서비스로 전달하지만 응답에는 노출하지 않는다")
    void dealerSignUpPassesPrivateKeyWithoutExposingIt() throws Exception {
        when(userService.signUp(any(SignUpCommand.class)))
                .thenReturn(new SignUpInfo(1L, "race_kim", "race@race.kr", "김레이스",
                        Role.GENERAL, DealerApplicationStatus.PENDING));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "race_kim",
                                  "email": "race@race.kr",
                                  "password": "password123",
                                  "realName": "김레이스",
                                  "phone": "01012345678",
                                  "role": "DEALER",
                                  "dealerLicenseKey": "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                // 딜러로 신청해도 승인 전까지는 일반 회원이다. 상태가 함께 내려가야
                // 클라이언트가 "심사 접수됨"과 "딜러 선택이 무시됨"을 구분한다
                .andExpect(jsonPath("$.role").value("GENERAL"))
                .andExpect(jsonPath("$.dealerApplicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.dealerLicenseKey").doesNotExist());

        ArgumentCaptor<SignUpCommand> command = ArgumentCaptor.forClass(SignUpCommand.class);
        verify(userService).signUp(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().dealerLicenseKey())
                .startsWith("dealer-licenses/");
    }

    @Test
    @DisplayName("잘못된 이메일과 짧은 비밀번호는 필드별 오류와 함께 400을 반환한다")
    void signUpValidationFailure() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "race_kim",
                                  "email": "not-an-email",
                                  "password": "123",
                                  "realName": "김레이스",
                                  "phone": "01012345678",
                                  "role": "GENERAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password")));
    }

    @Test
    @DisplayName("형식에 맞지 않는 아이디는 username 필드 오류와 함께 400을 반환한다")
    void signUpRejectsInvalidUsername() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("race_kim", "Race_Kim")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("username")));
    }

    @Test
    @DisplayName("특수문자로 이루어진 ASCII 비밀번호는 정상 처리된다")
    void signUpAcceptsSpecialCharacterPassword() throws Exception {
        when(userService.signUp(any(SignUpCommand.class)))
                .thenReturn(new SignUpInfo(1L, "race_kim", "race@race.kr", "김레이스", Role.GENERAL, null));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("password123", "P@ssw0rd!#$%^&*()_+-=~`")))
                .andExpect(status().isCreated());
    }

    // ASCII 제약이 풀리면 bcrypt 72바이트 한계를 넘겨 500이 나므로 400으로 막히는지 고정한다
    @Test
    @DisplayName("ASCII가 아닌 비밀번호는 password 필드 오류와 함께 400을 반환한다")
    void signUpRejectsNonAsciiPassword() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("password123", "비밀번호입니다123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("password")));
    }

    @Test
    @DisplayName("공백이 포함된 비밀번호는 password 필드 오류와 함께 400을 반환한다")
    void signUpRejectsPasswordWithWhitespace() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("password123", "pass word123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("password")));
    }

    @Test
    @DisplayName("정의되지 않은 역할 문자열은 INVALID_REQUEST로 400을 반환한다")
    void signUpRejectsUnknownRole() throws Exception {
        // Role 에 없는 값이어야 한다. 예전에는 ADMIN 을 썼지만 실제 역할이 되면서
        // 역직렬화를 통과해 버려, 이 테스트가 검사하려던 400 이 나오지 않았다
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("GENERAL", "SUPERVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("중복 이메일 비즈니스 예외는 USER_DUPLICATE_EMAIL로 409를 반환한다")
    void signUpDuplicateEmail() throws Exception {
        when(userService.signUp(any(SignUpCommand.class)))
                .thenThrow(new BusinessException(UserErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("하이픈이 섞인 휴대전화 번호는 phone 필드 오류와 함께 400을 반환한다")
    void signUpRejectsHyphenatedPhone() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("01012345678", "010-1234-5678")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("phone")));
    }

    @Test
    @DisplayName("010으로 시작하지 않거나 자릿수가 다른 번호는 phone 필드 오류와 함께 400을 반환한다")
    void signUpRejectsPhoneOutOfFormat() throws Exception {
        for (String invalidPhone : new String[]{"01112345678", "0101234567", "010123456789", "010abcd5678"}) {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequest().replace("01012345678", invalidPhone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[*].field", hasItems("phone")));
        }
    }

    @Test
    @DisplayName("중복 휴대전화 번호 비즈니스 예외는 USER_DUPLICATE_PHONE으로 409를 반환한다")
    void signUpDuplicatePhone() throws Exception {
        when(userService.signUp(any(SignUpCommand.class)))
                .thenThrow(new BusinessException(UserErrorCode.DUPLICATE_PHONE));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_DUPLICATE_PHONE"))
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 휴대전화 번호입니다."));
    }

    private static String validRequest() {
        return """
                {
                  "username": "race_kim",
                  "email": "race@race.kr",
                  "password": "password123",
                  "realName": "김레이스",
                  "phone": "01012345678",
                  "role": "GENERAL"
                }
                """;
    }
}
