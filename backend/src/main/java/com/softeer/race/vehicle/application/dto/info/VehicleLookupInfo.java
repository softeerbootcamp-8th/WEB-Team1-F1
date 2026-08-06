package com.softeer.race.vehicle.application.dto.info;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleSpec;

/**
 * 차량 조회 결과. 사용자가 "이 차가 내 차다"를 확인할 수 있는 만큼만 담는다.
 * <p>
 * {@link VehicleSpec}을 그대로 내보내지 않는 이유가 둘이다.
 * <p>
 * {@code basePrice}를 떼어낸다. 기준가가 응답에 실리면 시세 조회가 돌려주는 예상 시세와 나란히 놓고
 * 감가율을 역산할 수 있다({@code QuoteInfo}와 같은 이유).
 * <p>
 * {@code ownerName}도 떼어낸다. 호출자가 방금 입력한 값이라 되돌려줄 이유가 없고, 응답에 실을수록
 * 유출 경로만 늘어난다.
 * <p>
 * <b>주행거리가 없다.</b> 시점에 따라 변하는 값이라 원장이 들고 있을 수 없어 {@link VehicleSpec}에서
 * 빠졌다. 그래서 이 조회로는 주행거리를 알 수 없고, 시세 조회가 그 값을 따로 입력받는다.
 * <p>
 * 예상 시세도 담지 않는다. 주행거리를 모르는 상태에서는 계산할 수 없고, 계산해 봐도 아무것도
 * 보증하지 않는 금액이 된다.
 */
public record VehicleLookupInfo(
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        FuelType fuelType,
        Transmission transmission,
        String mainImageUrl
) {

    public static VehicleLookupInfo from(VehicleSpec spec) {
        return new VehicleLookupInfo(
                spec.plateNumber(),
                spec.manufacturer(),
                spec.model(),
                spec.modelYear(),
                spec.fuelType(),
                spec.transmission(),
                spec.mainImageUrl());
    }
}
