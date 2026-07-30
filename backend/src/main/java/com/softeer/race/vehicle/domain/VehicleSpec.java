package com.softeer.race.vehicle.domain;

/**
 * 차량 한 대의 제원. 제조사,모델,연식,주행거리,연료,변속기,소유자명,기준가,대표 이미지를 담는다.
 *
 * <p>차량 조회기가 돌려주는 값이자 시세 산정과 판매 신청이 함께 쓰는 계약이라,
 * 필드를 바꾸면 두 곳을 같이 본다.
 */
public record VehicleSpec(
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        Transmission transmission,
        String ownerName,
        /** 조회기가 준 참고가. 이 값 자체는 응답에 나가지 않고, 예상 시세는 서버가 이걸 입력으로 따로 산정한다 */
        long basePrice,
        String imageUrl
) {
}
