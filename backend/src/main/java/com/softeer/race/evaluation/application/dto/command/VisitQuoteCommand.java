package com.softeer.race.evaluation.application.dto.command;

import java.time.LocalDate;

/**
 * 방문견적 신청 유스케이스의 입력 전부. 행위 주체인 sellerId도 입력의 일부라 여기 담는다.
 * <p>
 * 차량 제원은 담지 않는다. 번호판으로 서버가 재조회하므로 클라이언트가 보낸 연식·주행거리를
 * 쓸 수 있는 경로 자체가 없어야 한다.
 */
public record VisitQuoteCommand(
        long sellerId,
        String plateNumber,
        String ownerName,
        String visitAddress,
        LocalDate visitDate,
        String contactPhone
) {
}
