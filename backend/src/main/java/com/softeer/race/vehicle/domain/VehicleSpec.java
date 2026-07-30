package com.softeer.race.vehicle.domain;

/**
 * 번호판으로 조회한 차량 제원. 판매 신청은 클라이언트가 보낸 제원을 믿지 않고 이 값으로 차량을 만든다.
 * <p>
 * {@code basePrice}는 원시 long이다. 기준가가 없으면 경매 시작가를 정할 수 없어 판매 신청 자체가
 * 성립하지 않으므로, {@link Vehicle#getEstimatedPrice()}가 nullable인 것과 달리 여기서는 열어두지 않는다.
 * <p>
 * 이미지는 대표 1건만 담는다. 여러 장이 필요해지면 리스트로 넓히는 순수 추가 변경이면 된다.
 */
public record VehicleSpec(
        String plateNumber,
        String ownerName,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        Transmission transmission,
        long basePrice,
        String mainImageUrl
) {
}
