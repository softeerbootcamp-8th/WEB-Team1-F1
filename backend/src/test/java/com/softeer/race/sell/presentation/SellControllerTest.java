package com.softeer.race.sell.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.sell.application.SellService;
import com.softeer.race.sell.application.dto.command.SellApplicationCommand;
import com.softeer.race.sell.application.dto.info.SellApplicationInfo;
import com.softeer.race.sell.exception.SellErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("판매 신청 컨트롤러")
class SellControllerTest {

    private static final long SELLER_ID = 90L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 7, 30, 21, 31);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellService sellService;

    // /api/sell은 인터셉터가 걸린 경로라 이 목이 실제로 호출된다
    // 인증을 통과시켜야 컨트롤러까지 도달하므로 모든 시나리오에서 스텁이 필요하다
    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(SELLER_ID));
    }

    @Test
    @DisplayName("정상 요청은 201과 경매 Location을 준다")
    void apply() throws Exception {
        given(sellService.apply(any(SellApplicationCommand.class))).willReturn(
                new SellApplicationInfo(1L, 1000L, 23_200_000L,
                        START_AT, START_AT.minusMinutes(30), START_AT.plusMinutes(20), "SCHEDULED"));

        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                // 판매 신청 자체를 조회할 엔드포인트가 없으므로 Location은 생성된 경매를 가리킨다
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/auctions/1"))
                .andExpect(jsonPath("$.auctionId").value(1))
                .andExpect(jsonPath("$.vehicleId").value(1000))
                .andExpect(jsonPath("$.startPrice").value(23200000))
                .andExpect(jsonPath("$.startAt").value("2026-07-30T21:31:00"))
                .andExpect(jsonPath("$.roomOpenAt").value("2026-07-30T21:01:00"))
                .andExpect(jsonPath("$.endAt").value("2026-07-30T21:51:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("번호판이 비어 있으면 필드 오류와 함께 400을 반환한다")
    void applyRejectsBlankPlateNumber() throws Exception {
        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("plateNumber")));
    }

    @Test
    @DisplayName("번호판 필드가 아예 없으면 400을 반환한다")
    void applyRejectsMissingPlateNumber() throws Exception {
        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("plateNumber")));
    }

    // 정규화를 하지 않기로 했으므로 공백·대시는 요청 단계에서 막혀야 한다
    @Test
    @DisplayName("공백이나 대시가 섞인 번호판은 400을 반환한다")
    void applyRejectsUnnormalizedPlateNumber() throws Exception {
        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber": "12가 3456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("plateNumber")));
    }

    @Test
    @DisplayName("카탈로그에 없는 번호판은 SELL_VEHICLE_NOT_FOUND로 404를 반환한다")
    void applyVehicleNotFound() throws Exception {
        given(sellService.apply(any(SellApplicationCommand.class)))
                .willThrow(new BusinessException(SellErrorCode.VEHICLE_NOT_FOUND));

        mockMvc.perform(post("/api/sell")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound())
                // 접두사가 없으면 AuctionErrorCode.VEHICLE_NOT_FOUND와 구별할 수 없다
                .andExpect(jsonPath("$.code").value("SELL_VEHICLE_NOT_FOUND"));
    }

    private static Cookie sessionCookie() {
        return new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token");
    }

    private static String validRequest() {
        return """
                {"plateNumber": "12가3456"}
                """;
    }
}
