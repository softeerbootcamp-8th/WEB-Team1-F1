package com.softeer.race.vehicle.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.exception.AuthErrorCode;
import com.softeer.race.auth.presentation.support.SessionCookieFactory;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
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

import java.util.Collections;
import java.util.List;

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
 *   <li>정상 요청은 200과 등록 결과를 준다</li>
 *   <li>이미지 목록이 비면 400</li>
 *   <li>허용 장수를 넘으면 400</li>
 *   <li>발급하지 않은 주소면 400 VEHICLE_UNMANAGED_IMAGE_URL</li>
 *   <li>없는 차량이면 404 VEHICLE_NOT_FOUND</li>
 *   <li>세션이 없으면 401이고 서비스까지 도달하지 않는다</li>
 * </ol>
 */
@WebMvcTest(controllers = VehicleImageController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("차량 사진 등록 컨트롤러")
class VehicleImageControllerTest {

    private static final long EVALUATOR_ID = 91L;
    private static final long VEHICLE_ID = 1000L;
    private static final String IMAGE_1 = "https://www.f1race.site/images/2026/08/a.jpg";
    private static final String IMAGE_2 = "https://www.f1race.site/images/2026/08/b.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleImageService vehicleImageService;

    /** 인터셉터가 걸린 경로라 이 목이 실제로 호출된다. 시나리오 6을 뺀 전부에서 통과시켜야 한다 */
    @MockitoBean
    private SessionService sessionService;

    @BeforeEach
    void before() {
        given(sessionService.authenticate(any())).willReturn(new AuthenticatedUser(EVALUATOR_ID));
    }

    @Test
    @DisplayName("정상 요청은 200과 등록 결과를 준다")
    void register() throws Exception {
        // given
        given(vehicleImageService.register(any(VehicleImageRegisterCommand.class))).willReturn(
                new VehicleImageRegisterInfo(VEHICLE_ID, List.of(
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_1, 1),
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_2, 2)), IMAGE_1));

        // when
        ResultActions response = request("""
                {"imageUrls": ["%s", "%s"]}
                """.formatted(IMAGE_1, IMAGE_2));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].imageUrl").value(IMAGE_1))
                .andExpect(jsonPath("$.images[0].sortOrder").value(1))
                .andExpect(jsonPath("$.images[1].sortOrder").value(2))
                .andExpect(jsonPath("$.thumbnailUrl").value(IMAGE_1));
    }

    @Test
    @DisplayName("이미지 목록이 비면 400")
    void registerRejectsEmpty() throws Exception {
        request("""
                {"imageUrls": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrls"));
    }

    @Test
    @DisplayName("허용 장수를 넘으면 400")
    void registerRejectsTooMany() throws Exception {
        // given : 상한이 20장이라 21장을 보낸다
        String urls = String.join(",", Collections.nCopies(21, "\"" + IMAGE_1 + "\""));

        // when & then
        request("{\"imageUrls\": [" + urls + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrls"));
    }

    @Test
    @DisplayName("발급하지 않은 주소면 400 VEHICLE_UNMANAGED_IMAGE_URL")
    void registerRejectsUnmanagedUrl() throws Exception {
        // given
        willThrow(new BusinessException(VehicleErrorCode.UNMANAGED_IMAGE_URL))
                .given(vehicleImageService).register(any(VehicleImageRegisterCommand.class));

        // when & then
        request("""
                {"imageUrls": ["https://evil.example.com/x.jpg"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VEHICLE_UNMANAGED_IMAGE_URL"));
    }

    @Test
    @DisplayName("없는 차량이면 404 VEHICLE_NOT_FOUND")
    void registerRejectsUnknownVehicle() throws Exception {
        // given
        willThrow(new BusinessException(VehicleErrorCode.NOT_FOUND))
                .given(vehicleImageService).register(any(VehicleImageRegisterCommand.class));

        // when & then
        request("""
                {"imageUrls": ["%s"]}
                """.formatted(IMAGE_1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("세션이 없으면 401이고 서비스까지 가지 않는다")
    void registerRequiresLogin() throws Exception {
        // given
        given(sessionService.authenticate(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHENTICATED));

        // when
        ResultActions response = mockMvc.perform(post("/api/vehicles/{vehicleId}/images", VEHICLE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"imageUrls": ["%s"]}
                        """.formatted(IMAGE_1)));

        // then
        response.andExpect(status().isUnauthorized());
        then(vehicleImageService).shouldHaveNoInteractions();
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/vehicles/{vehicleId}/images", VEHICLE_ID)
                .cookie(new Cookie(SessionCookieFactory.COOKIE_NAME, "raw-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
