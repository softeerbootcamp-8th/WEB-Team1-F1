package com.softeer.race.dealer.presentation.response;

import com.softeer.race.dealer.application.dto.info.DealerApplicationDetailInfo;
import com.softeer.race.dealer.domain.DealerApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "딜러 심사 신청 상세")
public record DealerApplicationDetailResponse(
        @Schema(description = "신청 ID", example = "1")
        Long id,

        @Schema(description = "신청자 회원 ID", example = "42")
        Long applicantId,

        @Schema(description = "신청자 아이디", example = "race_kim")
        String username,

        @Schema(description = "신청자 실명", example = "김레이스")
        String realName,

        @Schema(description = "신청자 이메일", example = "race@race.kr")
        String email,

        @Schema(description = "신청자 휴대전화 번호", example = "01012345678")
        String phone,

        @Schema(description = "심사 상태", example = "PENDING")
        DealerApplicationStatus status,

        @Schema(description = "반려 사유. 반려된 신청에만 있다", nullable = true)
        String rejectReason,

        @Schema(description = "신청 시각", example = "2026-08-16T15:04:05")
        LocalDateTime appliedAt,

        @Schema(description = "사원증을 볼 수 있는 임시 주소. 만료되면 상세를 다시 조회해 받는다")
        String licenseViewUrl,

        @Schema(description = "사원증 주소 만료 시각", example = "2026-08-16T15:19:05")
        LocalDateTime licenseViewExpiresAt
) {

    public static DealerApplicationDetailResponse from(DealerApplicationDetailInfo info) {
        return new DealerApplicationDetailResponse(
                info.id(),
                info.applicantId(),
                info.username(),
                info.realName(),
                info.email(),
                info.phone(),
                info.status(),
                info.rejectReason(),
                info.appliedAt(),
                info.licenseViewUrl(),
                info.licenseViewExpiresAt());
    }
}
