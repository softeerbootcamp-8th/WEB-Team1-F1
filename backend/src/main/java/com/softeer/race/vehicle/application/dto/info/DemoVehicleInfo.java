package com.softeer.race.vehicle.application.dto.info;

import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.infrastructure.DemoVehicleRow;

/**
 * 도움말에 실릴 데모 차량 한 대.
 * <p>
 * 대표 이미지를 담지 않는다. 도움말은 "무엇을 입력하면 되는지"를 알려주는 표라 썸네일이 할 일이 없고,
 * 넣는 만큼 응답 계약만 넓어진다.
 */
public record DemoVehicleInfo(
        String plateNumber,
        String ownerName,
        Manufacturer manufacturer,
        String model,
        int modelYear
) {

    public static DemoVehicleInfo from(DemoVehicleRow row) {
        return new DemoVehicleInfo(
                row.plateNumber(),
                row.ownerName(),
                row.manufacturer(),
                row.model(),
                row.modelYear());
    }
}