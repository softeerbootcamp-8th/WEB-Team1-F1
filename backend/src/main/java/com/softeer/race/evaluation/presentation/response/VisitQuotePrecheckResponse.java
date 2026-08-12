package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.VisitQuotePrecheckInfo;
import com.softeer.race.vehicle.presentation.response.VehicleLookupResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "방문견적 사전 확인 응답")
public record VisitQuotePrecheckResponse(

        @Schema(description = "확인된 차량 제원")
        VehicleLookupResponse vehicle,

        @Schema(description = "같은 번호판으로 진행 중인 방문견적이 있는지", example = "false")
        boolean hasInProgressVisitQuote
) {

    public static VisitQuotePrecheckResponse from(VisitQuotePrecheckInfo info) {
        return new VisitQuotePrecheckResponse(
                VehicleLookupResponse.from(info.vehicle()),
                info.hasInProgressVisitQuote());
    }
}
