package com.softeer.race.vehicle.presentation;

import com.softeer.race.auth.application.SessionService;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.presentation.GlobalExceptionHandler;
import com.softeer.race.vehicle.application.VehicleLookupService;
import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.exception.VehicleErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleLookupController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("차량 조회 컨트롤러")
class VehicleLookupControllerTest {

    private static final String IMAGE_URL = "https://cdn.race.dev/vehicles/grandeur-ig.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleLookupService vehicleLookupService;

    // @WebMvcTest 슬라이스는 WebMvcConfigurer 와 HandlerInterceptor 빈을 함께 스캔한다
    // AuthInterceptor 가 들어오면서 그 의존성인 SessionService 가 없어 컨텍스트 로딩이 실패하므로 채워 준다
    // 이 핸들러는 @LoginUser 를 받지 않아 인터셉터가 공개로 통과시키므로 이 목이 호출되지는 않는다
    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("세션 쿠키 없이도 200과 제원을 준다")
    void lookup() throws Exception {
        given(vehicleLookupService.lookup(any(VehicleLookupCommand.class))).willReturn(
                new VehicleLookupInfo("12가3456", Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                        FuelType.GASOLINE, Transmission.AUTOMATIC, IMAGE_URL));

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value("12가3456"))
                .andExpect(jsonPath("$.manufacturer").value("HYUNDAI"))
                .andExpect(jsonPath("$.model").value("그랜저 IG"))
                .andExpect(jsonPath("$.modelYear").value(2021))
                .andExpect(jsonPath("$.fuelType").value("GASOLINE"))
                .andExpect(jsonPath("$.transmission").value("AUTOMATIC"))
                .andExpect(jsonPath("$.mainImageUrl").value(IMAGE_URL))
                // 기준가는 예상 시세와 나란히 놓이면 감가율이 역산된다
                .andExpect(jsonPath("$.basePrice").doesNotExist())
                // 소유자명은 호출자가 방금 보낸 값이라 되돌려주지 않는다
                .andExpect(jsonPath("$.ownerName").doesNotExist())
                // 이 조회는 주행거리를 모르므로 시세를 산정할 수 없다
                .andExpect(jsonPath("$.estimatedPrice").doesNotExist())
                .andExpect(jsonPath("$.mileage").doesNotExist());
    }

    @Test
    @DisplayName("대표 이미지가 없는 차량은 mainImageUrl 이 null 이다")
    void lookupWithoutImage() throws Exception {
        given(vehicleLookupService.lookup(any(VehicleLookupCommand.class))).willReturn(
                new VehicleLookupInfo("90마5678", Manufacturer.BMW, "520i", 2020,
                        FuelType.GASOLINE, Transmission.AUTOMATIC, null));

        perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainImageUrl").value((Object) null));
    }

    @Test
    @DisplayName("번호판과 소유자명이 비어 있으면 필드 오류와 함께 400을 반환한다")
    void lookupRejectsBlankFields() throws Exception {
        perform("""
                {"plateNumber": "", "ownerName": ""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("plateNumber", "ownerName")));
    }

    // 정규화를 하지 않기로 했으므로 공백·대시는 요청 단계에서 막혀야 한다
    @Test
    @DisplayName("공백이나 대시가 섞인 번호판은 400을 반환한다")
    void lookupRejectsUnnormalizedPlateNumber() throws Exception {
        perform("""
                {"plateNumber": "12가 3456", "ownerName": "김민수"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("plateNumber")));
    }

    @Test
    @DisplayName("찾지 못하면 VEHICLE_SPEC_NOT_FOUND로 404를 반환한다")
    void lookupNotFound() throws Exception {
        given(vehicleLookupService.lookup(any(VehicleLookupCommand.class)))
                .willThrow(new BusinessException(VehicleErrorCode.SPEC_NOT_FOUND));

        perform(validRequest())
                .andExpect(status().isNotFound())
                // 등록된 vehicle 행이 없다는 VEHICLE_NOT_FOUND 와 구별돼야 원인을 가릴 수 있다
                .andExpect(jsonPath("$.code").value("VEHICLE_SPEC_NOT_FOUND"));
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(post("/api/vehicles/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String validRequest() {
        return """
                {"plateNumber": "12가3456", "ownerName": "김민수"}
                """;
    }
}
