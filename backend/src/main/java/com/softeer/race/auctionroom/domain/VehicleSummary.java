package com.softeer.race.auctionroom.domain;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;

/**
 * 경매방 화면에 보이는 차량 정보 (번호판 제외)
 */
public record VehicleSummary(
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType
) {
}