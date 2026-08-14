package com.softeer.race.vehicle.presentation.response;

import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 차량 조회 응답. 사용자가 "이 차가 내 차다"를 확인할 만큼만 담는다.
 * <p>
 * 기준가를 넣지 않는다. 시세 조회가 돌려주는 예상 시세와 나란히 놓이면 감가율이 역산된다.
 * 소유자명도 넣지 않는다 — 호출자가 방금 입력한 값이라 되돌려줄 이유가 없다.
 * <p>
 * 예상 시세와 주행거리를 넣지 않는다. 이 조회는 주행거리를 모르고, 시세는 그 값이 있어야 산정된다.
 * 시세가 필요한 화면은 주행거리를 입력받아 {@code POST /api/quotes}를 호출한다.
 */
@Schema(description = "차량 조회 응답")
public record VehicleLookupResponse(

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 표시명, 쪼개지 않는다", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear,

        @Schema(description = "연료", example = "GASOLINE")
        FuelType fuelType,

        @Schema(description = "대표 이미지 URL, 없는 차량은 null",
                example = "https://cdn.race.dev/vehicles/grandeur-ig.jpg")
        String mainImageUrl
) {

    public static VehicleLookupResponse from(VehicleLookupInfo info) {
        return new VehicleLookupResponse(
                info.plateNumber(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.fuelType(),
                info.mainImageUrl());
    }
}
