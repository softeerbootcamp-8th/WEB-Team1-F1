package com.softeer.race.dealer.presentation.response;

import com.softeer.race.dealer.application.dto.info.DealerApplicationInfo;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "딜러 심사 판정 결과")
public record DealerApplicationDecisionResponse(
        @Schema(description = "신청 ID", example = "1")
        Long id,

        @Schema(description = "판정 후 상태", example = "APPROVED")
        DealerApplicationStatus status,

        @Schema(description = "반려 사유. 승인이면 null", nullable = true)
        String rejectReason
) {

    public static DealerApplicationDecisionResponse from(DealerApplicationInfo info) {
        return new DealerApplicationDecisionResponse(info.id(), info.status(), info.rejectReason());
    }
}
