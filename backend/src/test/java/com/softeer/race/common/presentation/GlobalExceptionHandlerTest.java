package com.softeer.race.common.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 네 트랙이 모두 이 응답 계약에 의존하므로 실제 직렬화까지 확인한다
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("비즈니스 예외는 ErrorCode의 상태코드와 code를 담아 내려간다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("테스트 충돌입니다."))
                .andExpect(jsonPath("$.instance").value("/test/business"));
    }

    @Test
    @DisplayName("요청 본문 검증 실패는 400과 필드별 errors를 담아 내려간다")
    void requestBodyValidationFailure() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("쿼리 파라미터 검증 실패도 400과 errors를 담아 내려간다")
    void requestParamValidationFailure() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("매핑되지 않은 예외는 500으로 내려가고 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("서버 오류가 발생했습니다."));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestErrorCode.TEST_CONFLICT);
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Positive int size) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("DB 커넥션 풀 고갈");
        }
    }

    record TestRequest(@NotBlank String name) {
    }

    enum TestErrorCode implements ErrorCode {

        TEST_CONFLICT(HttpStatus.CONFLICT, "테스트 충돌입니다.");

        private final HttpStatus status;
        private final String message;

        TestErrorCode(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
