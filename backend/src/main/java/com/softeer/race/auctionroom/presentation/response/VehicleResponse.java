package com.softeer.race.auctionroom.presentation.response;

import com.softeer.race.auctionroom.domain.VehicleSummary;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경매 차량 요약")
public record VehicleResponse(
        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 표시명, 쪼개지 않는다", example = "더 뉴 셀토스")
        String model,

        @Schema(description = "연식", example = "2022")
        int modelYear,

        @Schema(description = "주행거리(km)", example = "35000")
        int mileage,

        @Schema(description = "연료", example = "GASOLINE")
        FuelType fuelType,

        @Schema(description = "대표 사진, 등록되지 않았으면 없다",
                example = "https://cdn.race.dev/avante-1.jpg")
        String thumbnailUrl,

        @Schema(description = "평가사가 남긴 진단서 PDF, 출품된 차량에는 항상 있다",
                example = "https://cdn.race.dev/avante-report.pdf")
        String diagnosticReportUrl
) {

    static VehicleResponse from(VehicleSummary vehicle) {
        return new VehicleResponse(
                vehicle.manufacturer(),
                vehicle.model(),
                vehicle.modelYear(),
                vehicle.mileage(),
                vehicle.fuelType(),
                vehicle.thumbnailUrl(),
                vehicle.diagnosticReportUrl());
    }
}