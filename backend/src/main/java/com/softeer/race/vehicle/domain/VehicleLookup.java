package com.softeer.race.vehicle.domain;

import java.util.Optional;

/**
 * 번호판으로 차량 제원을 조회하는 포트. 지금은 자체 카탈로그 테이블을 보지만 외부 API 구현으로 교체된다.
 * <p>
 * 조회 실패를 예외가 아니라 {@link Optional}로 알린다. 여기서 {@code BusinessException}을 던지면
 * 구현체가 특정 도메인의 {@code ErrorCode}에 묶여, 다른 유스케이스가 같은 포트를 다른 상태 코드로
 * 번역할 수 없게 된다. "없음"을 사실로만 전달하고 번역은 호출자가 한다.
 * <p>
 * 메서드가 둘인 이유는 호출자의 인증 수준이 다르기 때문이다. 판매 신청은 로그인으로 본인 확인이 끝나서
 * 번호판만으로 충분하지만, 시세 조회는 비회원이 호출하므로 소유자명까지 맞아야 한다.
 */
public interface VehicleLookup {

    /** 번호판으로 등록 차량 정보를 조회한다, 미등록이면 empty */
    Optional<VehicleSpec> findByPlateNumber(String plateNumber);

    /**
     * 번호판과 소유자명이 모두 맞는 차량 정보를 조회한다, 하나라도 어긋나면 empty.
     * <p>
     * 미등록인지 소유자명 불일치인지는 구분해 돌려주지 않는다. 구분할 수 있으면 호출자가 그대로 응답에
     * 흘릴 수 있고, 그 순간 번호판을 바꿔 넣어보며 소유자명을 역추적할 수 있게 된다.
     */
    Optional<VehicleSpec> find(String plateNumber, String ownerName);
}
