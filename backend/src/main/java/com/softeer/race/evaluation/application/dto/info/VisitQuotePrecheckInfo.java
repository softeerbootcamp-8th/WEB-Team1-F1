package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;

/**
 * 방문견적 예약 화면 진입 전 확인 결과. 차량 제원과 진행 중인 신청 존재 여부를 함께 돌려준다.
 */
public record VisitQuotePrecheckInfo(
        VehicleLookupInfo vehicle,
        boolean hasInProgressVisitQuote
) {
}
