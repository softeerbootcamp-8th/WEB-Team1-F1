package com.softeer.race.dealer.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.dealer.application.DealerApplicationReviewService;
import com.softeer.race.dealer.application.dto.info.DealerApplicationDetailInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.application.dto.info.DealerApplicationSummaryInfo;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.user.domain.Role;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminDealerApplicationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("관리자 딜러 심사 API")
class AdminDealerApplicationControllerTest {

    private static final String RAW_TOKEN = "admin-raw-token";
    private static final long ADMIN_ID = 1L;
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 8, 16, 15, 4, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DealerApplicationReviewService dealerApplicationReviewService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("기본 목록은 심사 대기 건만 돌려준다")
    void findAllDefaultsToPending() throws Exception {
        givenAdmin();
        given(dealerApplicationReviewService.findAllByStatus(DealerApplicationStatus.PENDING))
                .willReturn(List.of(new DealerApplicationSummaryInfo(
                        1L, 42L, "race_kim", "김레이스",
                        DealerApplicationStatus.PENDING, APPLIED_AT)));

        mockMvc.perform(get("/api/admin/dealer-applications").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications[0].username").value("race_kim"))
                // 목록에는 사원증 주소가 없어야 한다
                .andExpect(jsonPath("$.applications[0].licenseViewUrl").doesNotExist());
    }

    @Test
    @DisplayName("상세는 신청자 정보와 사원증 주소를 함께 돌려준다")
    void findDetailReturnsLicenseUrl() throws Exception {
        givenAdmin();
        given(dealerApplicationReviewService.findDetail(1L)).willReturn(
                new DealerApplicationDetailInfo(1L, 42L, "race_kim", "김레이스",
                        "race@race.kr", "01012345678", DealerApplicationStatus.PENDING, null,
                        APPLIED_AT, "https://s3.example/signed", "application/pdf",
                        APPLIED_AT.plusMinutes(15)));

        mockMvc.perform(get("/api/admin/dealer-applications/1").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseViewUrl").value("https://s3.example/signed"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.licenseContentType").value("application/pdf"));
    }

    @Test
    @DisplayName("승인은 200과 승인 상태를 돌려준다")
    void approve() throws Exception {
        givenAdmin();
        given(dealerApplicationReviewService.approve(1L)).willReturn(new DealerApplicationInfo(
                1L, DealerApplicationStatus.APPROVED, null, APPLIED_AT));

        mockMvc.perform(post("/api/admin/dealer-applications/1/approval").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("반려는 사유를 함께 돌려준다")
    void reject() throws Exception {
        givenAdmin();
        given(dealerApplicationReviewService.reject(any())).willReturn(new DealerApplicationInfo(
                1L, DealerApplicationStatus.REJECTED, "사원증 사진이 흐립니다.", APPLIED_AT));

        mockMvc.perform(post("/api/admin/dealer-applications/1/rejection")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사원증 사진이 흐립니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectReason").value("사원증 사진이 흐립니다."));
    }

    // 사유 없는 반려는 신청자에게 아무것도 알려주지 못한다
    @Test
    @DisplayName("반려 사유가 비면 400이다")
    void rejectRequiresReason() throws Exception {
        givenAdmin();

        mockMvc.perform(post("/api/admin/dealer-applications/1/rejection")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(dealerApplicationReviewService, never()).reject(any());
    }

    // 애너테이션이 아니라 /api/admin/** 경로가 막는다. 그 계약이 이 컨트롤러에서도 성립하는지 본다
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"GENERAL", "DEALER", "EVALUATOR"})
    @DisplayName("관리자가 아닌 역할은 승인할 수 없다")
    void approveRejectsNonAdmin(Role role) throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(ADMIN_ID, role));

        mockMvc.perform(post("/api/admin/dealer-applications/1/approval").cookie(adminCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(dealerApplicationReviewService, never()).approve(any());
    }

    @Test
    @DisplayName("로그인하지 않으면 목록도 볼 수 없다")
    void findAllRequiresLogin() throws Exception {
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        mockMvc.perform(get("/api/admin/dealer-applications"))
                .andExpect(status().isUnauthorized());
    }

    private void givenAdmin() {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(ADMIN_ID, Role.ADMIN));
    }

    private static Cookie adminCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN);
    }
}
