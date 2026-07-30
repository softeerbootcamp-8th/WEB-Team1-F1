package com.softeer.race.vehicle.domain;

import java.util.Optional;

/**
 * 번호판으로 차량 제원을 조회하는 포트. 지금은 인메모리 구현체뿐이지만 DB나 외부 API 구현으로 교체된다.
 * <p>
 * 조회 실패를 예외가 아니라 {@link Optional}로 알린다. 여기서 {@code BusinessException}을 던지면
 * 구현체가 특정 도메인의 {@code ErrorCode}에 묶여, 다른 유스케이스가 같은 포트를 다른 상태 코드로
 * 번역할 수 없게 된다. "없음"을 사실로만 전달하고 번역은 호출자가 한다.
 */
public interface VehicleLookup {

    /** 번호판으로 등록 차량 정보를 조회한다, 미등록이면 empty */
    Optional<VehicleSpec> findByPlateNumber(String plateNumber);
}
