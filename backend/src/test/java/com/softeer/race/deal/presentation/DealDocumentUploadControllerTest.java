package com.softeer.race.deal.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.deal.application.DealDocumentUploadService;
import com.softeer.race.deal.application.DealProgressService;
import com.softeer.race.deal.application.DealQueryService;
import com.softeer.race.deal.application.dto.DealDocumentUploadInfo;
import com.softeer.race.deal.exception.DealErrorCode;
import com.softeer.race.storage.domain.UploadContentType;
import com.softeer.race.user.domain.Role;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>정상 요청은 200과 업로드·조회 주소를 준다</li>
 *   <li>역할이 아니라 거래가 인가한다 — 평가사가 아닌 판매자도 이 경로는 통과한다</li>
 *   <li>자격이 없으면 서비스가 낸 코드가 그대로 나간다</li>
 *   <li>요청 검증에 걸리면 서비스까지 가지 않는다</li>
 *   <li>세션이 없으면 401이고 서비스까지 가지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = DealController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("명의이전 서류 업로드 주소 발급 컨트롤러")
class DealDocumentUploadControllerTest {

    private static final long DEAL_ID = 12L;
    private static final long SELLER_ID = 7L;

    private static final String PATH = "/api/deals/" + DEAL_ID + "/documents/presigned";

    private static final String KEY =
            "documents/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf";
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 12, 15, 30);

    private static final String PDF_REQUEST = """
            {"contentType": "application/pdf", "contentLength": 2481920}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealDocumentUploadService dealDocumentUploadService;

    @MockitoBean
    private DealQueryService dealQueryService;

    @MockitoBean
    private DealProgressService dealProgressService;

    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        // 판매자는 평가사가 아니다. 공용 발급 경로였다면 여기서 이미 403 이었을 역할이다
        given(sessionService.authenticate(any()))
                .willReturn(new AuthenticatedUser(SELLER_ID, Role.GENERAL));
    }

    @Test
    @DisplayName("판매자 요청은 200과 업로드·조회 주소를 준다")
    void issue() throws Exception {
        given(dealDocumentUploadService.issue(SELLER_ID, DEAL_ID, "application/pdf", 2_481_920L))
                .willReturn(new DealDocumentUploadInfo(KEY,
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/" + KEY,
                        "https://cdn.race.dev/" + KEY, EXPIRES_AT));

        // 명의이전 서류·탁송 출발 일정 제출에 넣어야 하는 값은 fileUrl 쪽이다
        request(PDF_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(KEY))
                .andExpect(jsonPath("$.fileUrl").value("https://cdn.race.dev/" + KEY))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-12T15:30:00"));
    }

    @Test
    @DisplayName("자격이 없으면 서비스가 낸 코드가 그대로 나간다")
    void issueRejectsWrongTurn() throws Exception {
        willThrow(new BusinessException(DealErrorCode.NOT_PARTICIPANT))
                .given(dealDocumentUploadService).issue(anyLong(), anyLong(), anyString(), anyLong());

        request(PDF_REQUEST)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
    }

    @Test
    @DisplayName("문서 상한을 넘는 크기는 서비스까지 가지 않는다")
    void issueRejectsTooLargeFile() throws Exception {
        request("""
                {"contentType": "application/pdf", "contentLength": %d}
                """.formatted(UploadContentType.MAX_DOCUMENT_SIZE + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentLength"));

        then(dealDocumentUploadService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("형식이 비면 서비스까지 가지 않는다")
    void issueRejectsBlankContentType() throws Exception {
        request("""
                {"contentType": " ", "contentLength": 2481920}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contentType"));

        then(dealDocumentUploadService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void issueRequiresSession() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED))
                .given(sessionService).authenticate(any());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PDF_REQUEST))
                .andExpect(status().isUnauthorized());

        then(dealDocumentUploadService).should(never())
                .issue(anyLong(), anyLong(), anyString(), anyLong());
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
