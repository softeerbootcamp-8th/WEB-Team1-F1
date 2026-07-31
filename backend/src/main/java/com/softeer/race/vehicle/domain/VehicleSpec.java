package com.softeer.race.vehicle.domain;

/**
 * 번호판으로 조회한 차량 제원. 판매 신청은 클라이언트가 보낸 제원을 믿지 않고 이 값으로 차량을 만든다.
 * <p>
 * {@code basePrice}는 그 모델의 기준가다. 개별 차량의 연식·주행거리가 반영되지 않은 값이므로
 * 예상 시세도 경매 시작가도 이 값을 그대로 쓰지 않고, QuotePolicy 가 감가를 뺀 결과를 쓴다.
 * 원시 long 인 이유는 기준가 없는 차량은 시세도 시작가도 정할 수 없어,
 * {@link Vehicle#getEstimatedPrice()}가 nullable 인 것과 달리 여기서는 열어둘 자리가 없기 때문이다.
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
