package com.softeer.race.dealer.presentation.response;

import com.softeer.race.dealer.application.dto.info.DealerApplicationSummaryInfo;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "딜러 심사 신청 목록의 한 건")
public record DealerApplicationSummaryResponse(
        @Schema(description = "신청 ID", example = "1")
        Long id,

        @Schema(description = "신청자 회원 ID", example = "42")
        Long applicantId,

        @Schema(description = "신청자 아이디", example = "race_kim")
        String username,

        @Schema(description = "신청자 실명", example = "김레이스")
        String realName,

        @Schema(description = "심사 상태", example = "PENDING")
        DealerApplicationStatus status,

        @Schema(description = "신청 시각", example = "2026-08-16T15:04:05")
        LocalDateTime appliedAt
) {

    public static DealerApplicationSummaryResponse from(DealerApplicationSummaryInfo info) {
        return new DealerApplicationSummaryResponse(
                info.id(),
                info.applicantId(),
                info.username(),
                info.realName(),
                info.status(),
                info.appliedAt());
    }
}
