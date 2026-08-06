package com.softeer.race.user.presentation;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
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
                .thenReturn(new SignUpInfo(1L, "race_kim", "race@race.kr", "김레이스", Role.GENERAL));

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
                                  "phone": "010-1234-5678",
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
                .thenReturn(new SignUpInfo(1L, "race_kim", "race@race.kr", "김레이스", Role.GENERAL));

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
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("GENERAL", "ADMIN")))
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

    private static String validRequest() {
        return """
                {
                  "username": "race_kim",
                  "email": "race@race.kr",
                  "password": "password123",
                  "realName": "김레이스",
                  "phone": "010-1234-5678",
                  "role": "GENERAL"
                }
                """;
    }
}
