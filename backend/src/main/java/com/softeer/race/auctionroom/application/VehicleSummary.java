package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RoomDetail;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.VehicleKeyword;

import java.util.List;

/**
 * 화면에 보이는 차량 요약, 방 조회와 개장 안내와 결과가 모두 싣는다
 */
public record VehicleSummary(
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        List<VehicleKeyword> keywords,
        List<String> imageUrls,
        String diagnosticReportUrl
) {

    // 사진과 키워드는 상세와 따로 읽어 오므로 조립하는 쪽에서 받는다
    static VehicleSummary of(RoomDetail detail, List<String> imageUrls, List<VehicleKeyword> keywords) {
        return new VehicleSummary(
                detail.manufacturer(),
                detail.model(),
                detail.modelYear(),
                detail.mileage(),
                detail.fuelType(),
                keywords,
                imageUrls,
                detail.diagnosticReportUrl());
    }
}
