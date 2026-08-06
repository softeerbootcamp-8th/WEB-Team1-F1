package com.softeer.race.auctionroom.domain;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;

/**
 * 경매방 화면에 보이는 차량 정보, 번호판은 경매에 필요하지 않고 차량을 특정할 수 있어 담지 않는다
 */
public record VehicleSummary(
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        String thumbnailUrl
) {
}