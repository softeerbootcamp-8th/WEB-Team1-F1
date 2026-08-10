package com.softeer.race.vehicle.domain;

/**
 * 여러 차량의 키워드를 한 번에 읽을 때 나오는 행 하나
 */
public record VehicleKeywordRow(
        Long vehicleId,
        VehicleKeyword keyword
) {
}
