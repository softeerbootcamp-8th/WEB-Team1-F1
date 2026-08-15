package com.softeer.race.vehicle.domain;

import java.util.Objects;

/**
 * 사람이 읽는 차량 이름, "현대 코나 SX2"
 * <p>
 * 조립 규칙을 한 곳에 둔다. 알림 문구가 종류마다 다른 모양으로 차량을 부르면 같은 차가
 * 서로 다른 차처럼 보인다.
 */
public record VehicleName(Manufacturer manufacturer, String model) {

    public VehicleName {
        Objects.requireNonNull(manufacturer, "제조사는 필수입니다.");

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("모델명은 비어 있을 수 없습니다.");
        }
    }

    public static VehicleName of(Vehicle vehicle) {
        return new VehicleName(vehicle.getManufacturer(), vehicle.getModel());
    }

    public String display() {
        return manufacturer.label() + " " + model;
    }
}
