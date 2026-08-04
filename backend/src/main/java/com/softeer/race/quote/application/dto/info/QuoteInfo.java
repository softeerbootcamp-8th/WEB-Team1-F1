package com.softeer.race.quote.application.dto.info;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleSpec;

/**
 * 시세 조회 결과. 차량 제원에 서버가 산정한 예상 시세를 붙인 것이다.
 * <p>
 * VehicleSpec 을 그대로 내보내지 않는 이유는 basePrice 를 떼어내야 하기 때문이다. 기준가가 응답에
 * 실리면 예상 시세와 나란히 놓고 감가율을 역산할 수 있다. 소유자명도 호출자가 방금 입력한 값이라
 * 되돌려주지 않는다 — 응답에 실을수록 유출 경로만 늘어난다.
 */
public record QuoteInfo(
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        Transmission transmission,
        String mainImageUrl,
        long estimatedPrice
) {

    /**
     * @param mileage 사용자가 신고한 주행거리. spec 이 들고 있지 않아 따로 받는다 —
     *                되돌려주는 이유는 화면이 "이 주행거리로 계산된 시세"임을 보여줘야 하기 때문이다
     */
    public static QuoteInfo of(VehicleSpec spec, int mileage, long estimatedPrice) {
        return new QuoteInfo(
                spec.plateNumber(),
                spec.manufacturer(),
                spec.model(),
                spec.modelYear(),
                mileage,
                spec.fuelType(),
                spec.transmission(),
                spec.mainImageUrl(),
                estimatedPrice);
    }
}
