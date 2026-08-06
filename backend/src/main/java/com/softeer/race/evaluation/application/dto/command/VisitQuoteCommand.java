package com.softeer.race.evaluation.application.dto.command;

import java.time.LocalDate;

/**
 * 방문견적 신청 유스케이스의 입력 전부. 행위 주체인 sellerId도 입력의 일부라 여기 담는다.
 * <p>
 * 차량 제원은 담지 않는다. 번호판으로 서버가 재조회하므로 클라이언트가 보낸 값을 쓸 수 있는 경로
 * 자체가 없어야 한다.
 * <p>
 * <b>주행거리도 받지 않는다.</b> 이 요청은 "이 차를 봐 주세요"라는 예약이고, 주행거리 실측과 시세
 * 산정은 평가사가 방문해서 하는 일이다. 신청자에게 물어 받으면 검증되지 않은 값이 차량에 남는다.
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
