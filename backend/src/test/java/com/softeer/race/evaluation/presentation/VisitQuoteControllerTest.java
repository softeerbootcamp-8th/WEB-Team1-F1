package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.evaluation.application.VisitQuoteService;
import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VisitQuoteController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("방문견적 신청 컨트롤러")
class VisitQuoteControllerTest {

    private static final long SELLER_ID = 90L;
    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 8, 20);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitQuoteService visitQuoteService;

    // /api/visit-quotes는 인터셉터가 걸린 경로라 이 목이 실제로 호출된다
    // 인증을 통과시켜야 컨트롤러까지 도달하므로 모든 시나리오에서 스텁이 필요하다
    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(SELLER_ID));
    }

    @Test
    @DisplayName("정상 요청은 201과 접수된 신청을 준다")
    void request() throws Exception {
        given(visitQuoteService.request(any(VisitQuoteCommand.class))).willReturn(
                new VisitQuoteInfo(1L, 1000L, PLATE_NUMBER,
                        VISIT_DATE, VISIT_ADDRESS, "REQUESTED", 23_200_000L));

        perform(validRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluationId").value(1))
                .andExpect(jsonPath("$.vehicleId").value(1000))
                .andExpect(jsonPath("$.plateNumber").value(PLATE_NUMBER))
                .andExpect(jsonPath("$.visitDate").value("2026-08-20"))
                .andExpect(jsonPath("$.visitAddress").value(VISIT_ADDRESS))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.estimatedPrice").value(23200000))
                // 연락처는 신청자가 방금 보낸 값이라 되돌려주지 않는다
                .andExpect(jsonPath("$.contactPhone").doesNotExist());
    }

    // 조회 API가 없어 Location에 넣을 주소가 전부 404를 가리키므로 헤더를 붙이지 않기로 했다
    @Test
    @DisplayName("Location 헤더는 붙이지 않는다")
    void requestHasNoLocationHeader() throws Exception {
        given(visitQuoteService.request(any(VisitQuoteCommand.class))).willReturn(
                new VisitQuoteInfo(1L, 1000L, PLATE_NUMBER,
                        VISIT_DATE, VISIT_ADDRESS, "REQUESTED", 23_200_000L));

        perform(validRequest())
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @DisplayName("필수 필드가 비어 있으면 필드 오류와 함께 400을 반환한다")
    void requestRejectsBlankFields() throws Exception {
        perform("""
                {"plateNumber": "", "ownerName": "", "mileage": null,
                 "visitAddress": "", "visitDate": null, "contactPhone": ""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems(
                        "plateNumber", "ownerName", "mileage", "visitAddress", "visitDate", "contactPhone")));
    }

    @Test
    @DisplayName("필드가 아예 없으면 400을 반환한다")
    void requestRejectsMissingFields() throws Exception {
        perform("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems(
                        "plateNumber", "ownerName", "mileage", "visitAddress", "visitDate", "contactPhone")));
    }

    // 화면 안내가 "'-'를 제외하고 숫자만"이므로 하이픈은 요청 단계에서 막혀야 한다
    // SignUpRequest처럼 하이픈을 허용하면 같은 번호가 두 형식으로 저장된다
    @Test
    @DisplayName("하이픈이 섞인 연락처는 400을 반환한다")
    void requestRejectsHyphenatedPhone() throws Exception {
        perform(body(PLATE_NUMBER, OWNER_NAME, VISIT_ADDRESS, "2026-08-20", "010-1234-5678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("contactPhone")));
    }

    @Test
    @DisplayName("날짜 형식이 아닌 방문일은 400을 반환한다")
    void requestRejectsMalformedVisitDate() throws Exception {
        perform(body(PLATE_NUMBER, OWNER_NAME, VISIT_ADDRESS, "2026/08/20", "01012345678"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("진행 중인 신청이 있으면 EVALUATION_DUPLICATE_REQUEST로 409를 반환한다")
    void requestDuplicate() throws Exception {
        given(visitQuoteService.request(any(VisitQuoteCommand.class)))
                .willThrow(new BusinessException(EvaluationErrorCode.DUPLICATE_REQUEST));

        perform(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("번호판이나 소유자명이 어긋나면 EVALUATION_VEHICLE_NOT_FOUND로 404를 반환한다")
    void requestVehicleNotFound() throws Exception {
        given(visitQuoteService.request(any(VisitQuoteCommand.class)))
                .willThrow(new BusinessException(EvaluationErrorCode.VEHICLE_NOT_FOUND));

        perform(validRequest())
                .andExpect(status().isNotFound())
                // 접두사가 없으면 SellErrorCode·AuctionErrorCode의 같은 이름과 구별할 수 없다
                .andExpect(jsonPath("$.code").value("EVALUATION_VEHICLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("과거 날짜는 EVALUATION_PAST_VISIT_DATE로 400을 반환한다")
    void requestPastVisitDate() throws Exception {
        given(visitQuoteService.request(any(VisitQuoteCommand.class)))
                .willThrow(new BusinessException(EvaluationErrorCode.PAST_VISIT_DATE));

        perform(validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EVALUATION_PAST_VISIT_DATE"));
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(post("/api/visit-quotes")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token");
    }

    private static String validRequest() {
        return body(PLATE_NUMBER, OWNER_NAME, VISIT_ADDRESS, "2026-08-20", "01012345678");
    }

    private static String body(String plateNumber, String ownerName, String visitAddress,
                               String visitDate, String contactPhone) {
        return """
                {"plateNumber": "%s", "ownerName": "%s", "mileage": 45000,
                 "visitAddress": "%s", "visitDate": "%s", "contactPhone": "%s"}
                """.formatted(plateNumber, ownerName, visitAddress, visitDate, contactPhone);
    }
}
