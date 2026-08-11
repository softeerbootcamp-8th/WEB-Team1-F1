package com.softeer.race.storage.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.storage.application.UploadService;
import com.softeer.race.storage.application.DealerLicenseUploadService;
import com.softeer.race.storage.application.dto.command.UploadCommand;
import com.softeer.race.storage.application.dto.info.UploadInfo;
import com.softeer.race.storage.domain.PresignedUpload;
import com.softeer.race.storage.exception.StorageErrorCode;
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
 *   <li>PDF 요청도 같은 경로로 받는다</li>
 *   <li>지원하지 않는 형식은 400 STORAGE_UNSUPPORTED_TYPE</li>
 *   <li>형식별 상한을 넘으면 400 STORAGE_FILE_TOO_LARGE</li>
 *   <li>파일 목록이 비면 400</li>
 *   <li>허용 건수를 넘으면 400</li>
 *   <li>절대 상한을 넘으면 400이고 어느 파일인지 알려준다</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = UploadController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("업로드 주소 발급 컨트롤러")
class UploadControllerTest {

    private static final long EVALUATOR_ID = 91L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 2, 15, 30);
    private static final String KEY = "images/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg";
    private static final String DOCUMENT_KEY =
            "documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";

    private static final String PATH = "/api/uploads/presigned";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private DealerLicenseUploadService dealerLicenseUploadService;

    /**
     * 발급 경로는 인터셉터가 걸려 있어 이 목이 실제로 호출된다. 인증을 통과시켜야 컨트롤러까지
     * 도달하므로 마지막 시나리오를 뺀 모든 시나리오에서 스텁이 필요하다.
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
        given(uploadService.issue(any(UploadCommand.class))).willReturn(
                new UploadInfo(List.of(new PresignedUpload(
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
    @DisplayName("PDF 요청도 같은 경로로 받고 documents 아래 키를 준다")
    void issueDocument() throws Exception {
        // given : 15MB 스캔 진단서. 이미지 상한(10MB)은 넘지만 문서 상한(20MB) 안이라
        //         요청 검증을 통과해 서비스까지 간다
        given(uploadService.issue(any(UploadCommand.class))).willReturn(
                new UploadInfo(List.of(new PresignedUpload(
                        DOCUMENT_KEY, "https://bucket.s3.ap-northeast-2.amazonaws.com/" + DOCUMENT_KEY,
                        "https://cdn.race.dev/" + DOCUMENT_KEY, EXPIRES_AT))));

        // when & then : 키 접두사가 갈라져 있어야 등록 단계에서 종류를 구별할 수 있다
        request("""
                {"files": [{"contentType": "application/pdf", "contentLength": 15728640}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads[0].key").value(DOCUMENT_KEY));
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 400 STORAGE_UNSUPPORTED_TYPE")
    void issueRejectsUnsupportedType() throws Exception {
        // given
        willThrow(new BusinessException(StorageErrorCode.UNSUPPORTED_TYPE))
                .given(uploadService).issue(any(UploadCommand.class));

        // when & then
        request("""
                {"files": [{"contentType": "image/gif", "contentLength": 100}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORAGE_UNSUPPORTED_TYPE"));
    }

    @Test
    @DisplayName("형식별 상한을 넘으면 400 STORAGE_FILE_TOO_LARGE")
    void issueRejectsOversizedFile() throws Exception {
        // given : 15MB짜리 JPEG은 절대 상한(20MB)을 통과해 요청 검증에 걸리지 않는다.
        //         형식별 상한은 형식을 아는 서비스가 판정하므로 여기서는 코드만 확인한다
        willThrow(new BusinessException(StorageErrorCode.FILE_TOO_LARGE))
                .given(uploadService).issue(any(UploadCommand.class));

        // when & then
        request("""
                {"files": [{"contentType": "image/jpeg", "contentLength": 15728640}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORAGE_FILE_TOO_LARGE"));
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
    @DisplayName("절대 상한인 20MB를 넘으면 400이고 어느 파일인지 알려준다")
    void issueRejectsTooLargeFile() throws Exception {
        // given : 두 번째 파일만 어떤 형식으로도 통과할 수 없는 크기다
        // when & then : 필드 경로에 인덱스가 있어야 프론트가 어느 파일을 지울지 알 수 있다
        request("""
                {"files": [
                  {"contentType": "image/jpeg", "contentLength": 100},
                  {"contentType": "application/pdf", "contentLength": 20971521}
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
        ResultActions response = mockMvc.perform(post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"files": [{"contentType": "image/jpeg", "contentLength": 100}]}
                        """));

        // then : 인터셉터가 막으므로 본문 파싱 전에 끝난다
        response.andExpect(status().isUnauthorized());
        then(uploadService).shouldHaveNoInteractions();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
