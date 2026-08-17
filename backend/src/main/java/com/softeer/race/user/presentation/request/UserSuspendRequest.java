package com.softeer.race.user.presentation.request;

import com.softeer.race.user.application.dto.command.SuspendUserCommand;
import com.softeer.race.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원 이용정지 요청")
public record UserSuspendRequest(
        // 상한을 엔티티 상수에서 끌어온다. 여기 숫자를 적으면 컬럼 폭과 따로 놀아,
        // 한쪽만 늘리는 순간 저장에서 잘리거나 터진다
        @Schema(description = "관리자가 남기는 정지 사유. 당사자에게는 내려가지 않는다",
                example = "허위 매물을 반복 등록했습니다.")
        @NotBlank(message = "정지 사유는 필수입니다.")
        @Size(max = User.MAX_SUSPEND_REASON_LENGTH)
        String reason
) {

    public SuspendUserCommand toCommand(Long userId) {
        return new SuspendUserCommand(userId, reason);
    }
}
