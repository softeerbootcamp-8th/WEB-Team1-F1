package com.softeer.race.dealer.presentation.request;

import com.softeer.race.dealer.application.dto.command.RejectDealerApplicationCommand;
import com.softeer.race.dealer.domain.DealerApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "딜러 심사 반려 요청")
public record DealerApplicationRejectRequest(
        // 상한을 엔티티 상수에서 끌어온다. 여기 숫자를 적으면 컬럼 폭과 따로 놀아,
        // 한쪽만 늘리는 순간 저장에서 잘리거나 터진다
        @Schema(description = "신청자에게 전달할 반려 사유",
                example = "사원증 사진이 흐려 확인할 수 없습니다.")
        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = DealerApplication.MAX_REJECT_REASON_LENGTH)
        String reason
) {

    public RejectDealerApplicationCommand toCommand(Long applicationId) {
        return new RejectDealerApplicationCommand(applicationId, reason);
    }
}
