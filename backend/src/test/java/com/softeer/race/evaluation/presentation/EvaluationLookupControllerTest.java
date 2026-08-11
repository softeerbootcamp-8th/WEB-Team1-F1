package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.evaluation.application.EvaluationLookupService;
import com.softeer.race.evaluation.application.dto.info.EvaluationDetailInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오
 * <ol>
 *   <li>내 신청 목록은 세션의 사용자로 조회한다</li>
 *   <li>내 담당 목록은 다른 서비스 메서드를 탄다</li>
 *   <li>목록이 비면 빈 배열이다</li>
 *   <li>상세는 결과 칸까지 내려준다</li>
 *   <li>진단 전 상세는 결과 칸이 null로 나간다</li>
 *   <li>권한이 없으면 404</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = EvaluationLookupController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("방문견적 조회 컨트롤러")
class EvaluationLookupControllerTest {

    private static final long USER_ID = 600L;
    private static final long EVALUATION_ID = 600L;
    private static final long VEHICLE_ID = 6000L;

    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 8, 5, 10, 0);
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 8, 5, 18, 0);

    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";
    private static final String IMAGE_URL = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String CONTACT_PHONE = "01012345678";
    private static final String REJECT_REASON = "번호판이 등록된 차량과 일치하지 않습니다.";
    private static final String EVALUATOR_NAME = "박평가";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationLookupService evaluationLookupService;

    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(USER_ID));
    }

    @Test
    @DisplayName("내 신청 목록은 세션의 사용자로 조회한다")
    void findMyRequests() throws Exception {
        // given : 조회 대상이 본문이나 쿼리에서 오면 남의 목록을 볼 수 있다
        given(evaluationLookupService.findMyRequests(USER_ID)).willReturn(List.of(summary()));

        // when & then
        mockMvc.perform(get("/api/evaluations/my-requests").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(1))
                .andExpect(jsonPath("$.evaluations[0].evaluationId").value(EVALUATION_ID))
                .andExpect(jsonPath("$.evaluations[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.evaluations[0].plateNumber").value("12가3456"))
                .andExpect(jsonPath("$.evaluations[0].visitDate").value("2026-08-20"))
                // 배정돼도 status는 REQUESTED로 남으므로 이 값이 없으면
                // 접수 직후와 평가사가 정해진 뒤가 화면에서 똑같다
                .andExpect(jsonPath("$.evaluations[0].assigned").value(true));
    }

    @Test
    @DisplayName("내 담당 목록은 다른 서비스 메서드를 탄다")
    void findMyAssignments() throws Exception {
        // given : 두 경로가 같은 메서드를 부르면 판매자와 평가사가 서로의 목록을 본다
        given(evaluationLookupService.findMyAssignments(USER_ID)).willReturn(List.of(summary()));

        // when & then
        mockMvc.perform(get("/api/evaluations/my-assignments").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(1));

        then(evaluationLookupService).should().findMyAssignments(USER_ID);
    }

    @Test
    @DisplayName("목록이 비면 빈 배열이다")
    void findMyRequestsWhenEmpty() throws Exception {
        // given : null이 아니라 빈 배열이어야 화면이 분기 없이 렌더할 수 있다
        given(evaluationLookupService.findMyRequests(USER_ID)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/evaluations/my-requests").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations").isArray())
                .andExpect(jsonPath("$.evaluations.length()").value(0));
    }

    @Test
    @DisplayName("상세는 결과 칸까지 내려준다")
    void findDetail() throws Exception {
        // given
        given(evaluationLookupService.findDetail(EVALUATION_ID, USER_ID)).willReturn(detail(
                45_000, 21_500_000L, List.of(IMAGE_URL), DOCUMENT_URL, SUBMITTED_AT));

        // when & then
        mockMvc.perform(get("/api/evaluations/" + EVALUATION_ID).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID))
                .andExpect(jsonPath("$.visitAddress").value("서울 성동구 왕십리로 83"))
                .andExpect(jsonPath("$.mileage").value(45000))
                .andExpect(jsonPath("$.estimatedPrice").value(21500000))
                .andExpect(jsonPath("$.imageUrls[0]").value(IMAGE_URL))
                .andExpect(jsonPath("$.diagnosticReportUrl").value(DOCUMENT_URL))
                .andExpect(jsonPath("$.submittedAt").value("2026-08-05T18:00:00"))
                // 평가사가 방문 전 연락에 쓴다. 배정 응답은 한 번 주고 끝이라 여기가 유일한 재조회처다
                .andExpect(jsonPath("$.contactPhone").value(CONTACT_PHONE))
                .andExpect(jsonPath("$.evaluatorName").value(EVALUATOR_NAME));
    }

    @Test
    @DisplayName("진단 전 상세는 결과 칸이 null로 나간다")
    void findDetailBeforeDiagnosis() throws Exception {
        // given : 비어 있음이 곧 "아직 평가사가 다녀가지 않았다"이다
        given(evaluationLookupService.findDetail(EVALUATION_ID, USER_ID))
                .willReturn(detail(null, null, List.of(), null, null));

        // when & then
        mockMvc.perform(get("/api/evaluations/" + EVALUATION_ID).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").doesNotExist())
                .andExpect(jsonPath("$.estimatedPrice").doesNotExist())
                .andExpect(jsonPath("$.diagnosticReportUrl").doesNotExist())
                .andExpect(jsonPath("$.submittedAt").doesNotExist())
                // 반려되지 않았으므로 사유 칸도 없다. 빈 문자열로 나가면 화면이
                // "사유 없이 반려됨"과 "반려되지 않음"을 구분하지 못한다
                .andExpect(jsonPath("$.rejectReason").doesNotExist());
    }

    // 판매자가 반려 사유를 읽는 유일한 경로다. Info에서 Response로 옮기다 빠뜨리면
    // 사유가 저장은 되는데 화면에는 끝내 나타나지 않는다
    @Test
    @DisplayName("반려된 상세는 사유를 내려주고 결과 칸은 비어 있다")
    void findDetailWhenRejected() throws Exception {
        // given
        given(evaluationLookupService.findDetail(EVALUATION_ID, USER_ID)).willReturn(
                detail("REJECTED", null, null, List.of(), null, null, REJECT_REASON));

        // when & then
        mockMvc.perform(get("/api/evaluations/" + EVALUATION_ID).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value(REJECT_REASON))
                .andExpect(jsonPath("$.mileage").doesNotExist());
    }

    @Test
    @DisplayName("권한이 없으면 404")
    void findDetailHidesFromStranger() throws Exception {
        // given
        willThrow(new BusinessException(EvaluationErrorCode.NOT_FOUND))
                .given(evaluationLookupService).findDetail(anyLong(), anyLong());

        // when & then
        mockMvc.perform(get("/api/evaluations/" + EVALUATION_ID).cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVALUATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void requiresLogin() throws Exception {
        // given
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        // when & then
        mockMvc.perform(get("/api/evaluations/my-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/evaluations/my-assignments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/evaluations/" + EVALUATION_ID))
                .andExpect(status().isUnauthorized());

        then(evaluationLookupService).shouldHaveNoInteractions();
    }

    private static EvaluationSummaryInfo summary() {
        return new EvaluationSummaryInfo(EVALUATION_ID, "APPROVED", true, "12가3456",
                Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                VISIT_DATE, "서울 성동구 왕십리로 83", REQUESTED_AT);
    }

    private static EvaluationDetailInfo detail(Integer mileage, Long estimatedPrice,
                                               List<String> imageUrls, String diagnosticReportUrl,
                                               LocalDateTime submittedAt) {
        return detail("APPROVED", mileage, estimatedPrice, imageUrls, diagnosticReportUrl,
                submittedAt, null);
    }

    private static EvaluationDetailInfo detail(String status, Integer mileage, Long estimatedPrice,
                                               List<String> imageUrls, String diagnosticReportUrl,
                                               LocalDateTime submittedAt, String rejectReason) {
        return new EvaluationDetailInfo(
                EVALUATION_ID, status, VISIT_DATE, "서울 성동구 왕십리로 83",
                CONTACT_PHONE, REQUESTED_AT, EVALUATOR_NAME,
                VEHICLE_ID, "12가3456", Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                mileage, estimatedPrice, imageUrls, diagnosticReportUrl, submittedAt,
                List.of(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.NO_LEAK), rejectReason);
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token");
    }
}
