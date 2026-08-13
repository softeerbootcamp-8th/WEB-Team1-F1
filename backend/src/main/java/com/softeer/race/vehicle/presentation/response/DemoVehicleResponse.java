package com.softeer.race.vehicle.presentation.response;

import com.softeer.race.vehicle.application.dto.info.DemoVehicleInfo;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 데모 차량 안내 응답. 실제 회원이 아니라 카탈로그의 가상 차량만 담긴다.
 * <p>
 * 소유자명을 내려준다. 차량 조회 응답이 소유자명을 빼는 것과 반대인데, 그쪽은 호출자가 방금 입력한
 * 값을 되돌려주는 것이고 이쪽은 무엇을 입력해야 하는지를 알려주는 것이라 목적이 반대다.
 */
@Schema(description = "데모 차량 안내 응답")
public record DemoVehicleResponse(

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "소유자 이름", example = "김민수")
        String ownerName,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 표시명, 쪼개지 않는다", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear
) {

    public static List<DemoVehicleResponse> from(List<DemoVehicleInfo> infos) {
        return infos.stream()
                .map(info -> new DemoVehicleResponse(
                        info.plateNumber(),
                        info.ownerName(),
                        info.manufacturer(),
                        info.model(),
                        info.modelYear()))
                .toList();
    }
}