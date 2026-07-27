package com.softeer.race.user.presentation;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.user.application.UserService;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.exception.UserErrorCode;
import com.softeer.race.user.presentation.dto.request.SignUpRequest;
import com.softeer.race.user.presentation.dto.response.SignUpResponse;
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

    @Test
    @DisplayName("정상 회원가입 요청은 비밀번호 없이 201 응답을 반환한다")
    void signUp() throws Exception {
        when(userService.signUp(any(SignUpRequest.class)))
                .thenReturn(new SignUpResponse(1L, "race@race.kr", "레이스", Role.GENERAL));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("race@race.kr"))
                .andExpect(jsonPath("$.nickname").value("레이스"))
                .andExpect(jsonPath("$.role").value("GENERAL"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 이메일과 짧은 비밀번호는 필드별 오류와 함께 400을 반환한다")
    void signUpValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "123",
                                  "nickname": "레이스",
                                  "phone": "010-1234-5678",
                                  "address": "서울시 강남구 테헤란로 123",
                                  "role": "GENERAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password")));
    }

    @Test
    @DisplayName("정의되지 않은 역할 문자열은 INVALID_REQUEST로 400을 반환한다")
    void signUpRejectsUnknownRole() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("GENERAL", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("중복 이메일 비즈니스 예외는 USER_DUPLICATE_EMAIL로 409를 반환한다")
    void signUpDuplicateEmail() throws Exception {
        when(userService.signUp(any(SignUpRequest.class)))
                .thenThrow(new BusinessException(UserErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 이메일입니다."));
    }

    private static String validRequest() {
        return """
                {
                  "email": "race@race.kr",
                  "password": "password123",
                  "nickname": "레이스",
                  "phone": "010-1234-5678",
                  "address": "서울시 강남구 테헤란로 123",
                  "role": "GENERAL"
                }
                """;
    }
}
