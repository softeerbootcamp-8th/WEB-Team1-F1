package com.softeer.race.image.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.image.application.ImageUploadService;
import com.softeer.race.image.application.dto.command.ImageUploadCommand;
import com.softeer.race.image.application.dto.info.ImageUploadInfo;
import com.softeer.race.image.domain.PresignedUpload;
import com.softeer.race.image.exception.ImageErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>정상 요청은 200과 발급 결과를 준다</li>
 *   <li>지원하지 않는 형식은 400 IMAGE_UNSUPPORTED_TYPE</li>
 *   <li>파일 목록이 비면 400</li>
 *   <li>허용 건수를 넘으면 400</li>
 *   <li>파일 크기가 상한을 넘으면 400이고 어느 파일인지 알려준다</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = ImageUploadController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("평가 사진 업로드 컨트롤러")
class ImageUploadControllerTest {

    private static final long EVALUATOR_ID = 91L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 2, 15, 30);
    private static final String KEY = "images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageUploadService evaluationImageService;

    /**
     * 발급 경로는 인터셉터가 걸려 있어 이 목이 실제로 호출된다. 인증을 통과시켜야 컨트롤러까지
     * 도달하므로 시나리오 6을 뺀 모든 시나리오에서 스텁이 필요하다.
     */
    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(EVALUATOR_ID));
    }

    @Test
    @DisplayName("정상 요청은 200과 업로드 주소를 준다")
    void issue() throws Exception {
        // given
        given(evaluationImageService.issue(any(ImageUploadCommand.class))).willReturn(
                new ImageUploadInfo(List.of(new PresignedUpload(
                        KEY, "https://bucket.s3.ap-northeast-2.amazonaws.com/" + KEY + "?X-Amz-Signature=x",
                        "https://cdn.race.dev/" + KEY, EXPIRES_AT))));

        // when
        ResultActions response = request("""
                {"files": [{"contentType": "image/jpeg", "contentLength": 2481920}]}
                """);

        // then : 업로드 주소와 조회 주소의 호스트가 다르다, 저장해야 하는 값은 fileUrl 쪽이다
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads.length()").value(1))
                .andExpect(jsonPath("$.uploads[0].key").value(KEY))
                .andExpect(jsonPath("$.uploads[0].uploadUrl").value(
                        containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.uploads[0].fileUrl").value("https://cdn.race.dev/" + KEY))
                .andExpect(jsonPath("$.uploads[0].expiresAt").value("2026-08-02T15:30:00"));
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 400 IMAGE_UNSUPPORTED_TYPE")
    void issueRejectsUnsupportedType() throws Exception {
        // given
        willThrow(new BusinessException(ImageErrorCode.UNSUPPORTED_TYPE))
                .given(evaluationImageService).issue(any(ImageUploadCommand.class));

        // when & then
        request("""
                {"files": [{"contentType": "application/pdf", "contentLength": 100}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_UNSUPPORTED_TYPE"));
    }

    @Test
    @DisplayName("파일 목록이 비면 400")
    void issueRejectsEmptyFiles() throws Exception {
        request("""
                {"files": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("files"));
    }

    @Test
    @DisplayName("허용 건수를 넘으면 400")
    void issueRejectsTooManyFiles() throws Exception {
        // given : 상한이 20건이라 21건을 보낸다
        String file = "{\"contentType\": \"image/jpeg\", \"contentLength\": 100}";
        String files = String.join(",", Collections.nCopies(21, file));

        // when & then
        request("{\"files\": [" + files + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("files"));
    }

    @Test
    @DisplayName("파일 크기가 10MB를 넘으면 400이고 어느 파일인지 알려준다")
    void issueRejectsTooLargeFile() throws Exception {
        // given : 두 번째 파일만 상한을 넘긴다
        // when & then : 필드 경로에 인덱스가 있어야 프론트가 어느 파일을 지울지 알 수 있다
        request("""
                {"files": [
                  {"contentType": "image/jpeg", "contentLength": 100},
                  {"contentType": "image/jpeg", "contentLength": 10485761}
                ]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("files[1].contentLength"));
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void issueRequiresLogin() throws Exception {
        // given
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        // when
        ResultActions response = mockMvc.perform(post("/api/images/presigned")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"files": [{"contentType": "image/jpeg", "contentLength": 100}]}
                        """));

        // then : 인터셉터가 막으므로 본문 파싱 전에 끝난다
        response.andExpect(status().isUnauthorized());
        then(evaluationImageService).shouldHaveNoInteractions();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/images/presigned")
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
