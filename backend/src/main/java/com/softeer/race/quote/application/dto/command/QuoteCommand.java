package com.softeer.race.quote.application.dto.command;

/**
 * 시세 조회 입력.
 * <p>
 * 소유자명은 인증 수단이 아니라 조회 조건이다. 비회원도, 자기 차가 아니어도 두 값만 알면 조회된다.
 * 대신 번호판만으로는 찾을 수 없어서 대입으로 소유자명을 알아낼 수 없다.
 */
public record QuoteCommand(String plateNumber, String ownerName) {
}
