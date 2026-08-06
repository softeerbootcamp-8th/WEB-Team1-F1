package com.softeer.race.vehicle.application.dto.command;

/**
 * 차량 조회 입력.
 * <p>
 * 행위 주체가 없다. 비회원도 호출하는 조회라 세션에서 꺼낼 것이 없고, 소유자명이 인증 수단이 아니라
 * 조회 조건이다 — 자기 차가 아니어도 두 값을 알면 조회된다. 대신 번호판만으로는 찾을 수 없어서
 * 대입으로 소유자명을 알아낼 수 없다.
 */
public record VehicleLookupCommand(String plateNumber, String ownerName) {
}
