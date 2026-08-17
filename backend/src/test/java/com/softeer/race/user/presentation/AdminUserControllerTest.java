package com.softeer.race.user.presentation;

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
import com.softeer.race.user.application.AdminUserQueryService;
import com.softeer.race.user.application.UserSuspensionService;
import com.softeer.race.user.application.dto.command.SearchUsersCommand;
import com.softeer.race.user.application.dto.info.UserDetailInfo;
import com.softeer.race.user.application.dto.info.UserPageInfo;
import com.softeer.race.user.application.dto.info.UserStatusInfo;
import com.softeer.race.user.application.dto.info.UserSummaryInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;
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

@WebMvcTest(controllers = AdminUserController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("관리자 회원 관리 API")
class AdminUserControllerTest {

    private static final String RAW_TOKEN = "admin-raw-token";
    private static final long ADMIN_ID = 1L;
    private static final long TARGET_ID = 42L;
    private static final String REASON = "허위 매물을 반복 등록했습니다.";
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserQueryService adminUserQueryService;

    @MockitoBean
    private UserSuspensionService userSuspensionService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("목록은 회원과 총 건수를 함께 돌려준다")
    void search() throws Exception {
        givenAdmin();
        given(adminUserQueryService.search(any())).willReturn(new UserPageInfo(
                List.of(new UserSummaryInfo(TARGET_ID, "race_kim", "김레이스",
                        Role.DEALER, UserStatus.SUSPENDED, JOINED_AT)),
                0, 3, 47));

        mockMvc.perform(get("/api/admin/users").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("race_kim"))
                .andExpect(jsonPath("$.totalUsers").value(47))
                .andExpect(jsonPath("$.totalPages").value(3))
                // 목록에는 연락 수단과 정지 사유가 없어야 한다
                .andExpect(jsonPath("$.users[0].phone").doesNotExist())
                .andExpect(jsonPath("$.users[0].suspendReason").doesNotExist());
    }

    @Test
    @DisplayName("검색어와 필터가 그대로 조회 조건이 된다")
    void searchPassesConditions() throws Exception {
        givenAdmin();
        given(adminUserQueryService.search(any()))
                .willReturn(new UserPageInfo(List.of(), 2, 0, 0));

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "race_kim")
                        .param("role", "DEALER")
                        .param("status", "SUSPENDED")
                        .param("page", "2")
                        .cookie(adminCookie()))
                .andExpect(status().isOk());

        verify(adminUserQueryService).search(
                new SearchUsersCommand("race_kim", Role.DEALER, UserStatus.SUSPENDED, 2));
    }

    // 조건 없이 부르는 것이 첫 화면이다. 딜러 심사와 달리 기본 필터를 두지 않는다
    @Test
    @DisplayName("조건을 주지 않으면 전체 회원의 첫 페이지를 조회한다")
    void searchDefaultsToFirstPageOfEveryone() throws Exception {
        givenAdmin();
        given(adminUserQueryService.search(any()))
                .willReturn(new UserPageInfo(List.of(), 0, 0, 0));

        mockMvc.perform(get("/api/admin/users").cookie(adminCookie()))
                .andExpect(status().isOk());

        verify(adminUserQueryService).search(new SearchUsersCommand(null, null, null, 0));
    }

    // 음수를 그대로 흘리면 PageRequest 가 IllegalArgumentException 을 던져 500 이 된다
    @Test
    @DisplayName("음수 페이지는 400이다")
    void searchRejectsNegativePage() throws Exception {
        givenAdmin();

        mockMvc.perform(get("/api/admin/users").param("page", "-1").cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(adminUserQueryService, never()).search(any());
    }

    @Test
    @DisplayName("상세는 연락 수단과 정지 사유까지 돌려준다")
    void findDetail() throws Exception {
        givenAdmin();
        given(adminUserQueryService.findDetail(TARGET_ID)).willReturn(new UserDetailInfo(
                TARGET_ID, "race_kim", "김레이스", "race@race.kr", "01012345678",
                Role.DEALER, UserStatus.SUSPENDED, REASON, JOINED_AT));

        mockMvc.perform(get("/api/admin/users/42").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("race@race.kr"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.suspendReason").value(REASON));
    }

    @Test
    @DisplayName("정지는 200과 정지 상태·사유를 돌려준다")
    void suspend() throws Exception {
        givenAdmin();
        given(userSuspensionService.suspend(any())).willReturn(
                new UserStatusInfo(TARGET_ID, Role.DEALER, UserStatus.SUSPENDED, REASON));

        mockMvc.perform(post("/api/admin/users/42/suspension")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspendReason").value(REASON))
                // 정지는 역할을 바꾸지 않는다
                .andExpect(jsonPath("$.role").value("DEALER"));
    }

    @Test
    @DisplayName("해제는 사유가 지워진 활성 상태를 돌려준다")
    void activate() throws Exception {
        givenAdmin();
        given(userSuspensionService.activate(TARGET_ID)).willReturn(
                new UserStatusInfo(TARGET_ID, Role.DEALER, UserStatus.ACTIVE, null));

        mockMvc.perform(post("/api/admin/users/42/activation").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspendReason").isEmpty());
    }

    // 사유 없는 정지는 나중에 왜 막았는지 아무도 알 수 없다
    @Test
    @DisplayName("정지 사유가 비면 400이다")
    void suspendRequiresReason() throws Exception {
        givenAdmin();

        mockMvc.perform(post("/api/admin/users/42/suspension")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(userSuspensionService, never()).suspend(any());
    }

    // 애너테이션이 아니라 /api/admin/** 경로가 막는다. 그 계약이 이 컨트롤러에서도 성립하는지 본다
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"GENERAL", "DEALER", "EVALUATOR"})
    @DisplayName("관리자가 아닌 역할은 회원을 정지할 수 없다")
    void suspendRejectsNonAdmin(Role role) throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(ADMIN_ID, role));

        mockMvc.perform(post("/api/admin/users/42/suspension")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(userSuspensionService, never()).suspend(any());
    }

    // 목록은 회원 개인정보를 스무 건씩 내보내는 자리라 차단이 뚫리면 피해가 조작보다 크다
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"GENERAL", "DEALER", "EVALUATOR"})
    @DisplayName("관리자가 아닌 역할은 회원 목록을 볼 수 없다")
    void searchRejectsNonAdmin(Role role) throws Exception {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(ADMIN_ID, role));

        mockMvc.perform(get("/api/admin/users").cookie(adminCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verify(adminUserQueryService, never()).search(any());
    }

    @Test
    @DisplayName("로그인하지 않으면 해제할 수 없다")
    void activateRequiresLogin() throws Exception {
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        mockMvc.perform(post("/api/admin/users/42/activation"))
                .andExpect(status().isUnauthorized());

        verify(userSuspensionService, never()).activate(any());
    }

    private void givenAdmin() {
        given(sessionService.authenticate(RAW_TOKEN))
                .willReturn(new AuthenticatedUser(ADMIN_ID, Role.ADMIN));
    }

    private static Cookie adminCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, RAW_TOKEN);
    }
}
