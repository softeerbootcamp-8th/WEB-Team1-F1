package com.softeer.race.user.presentation.response;

import com.softeer.race.user.application.dto.info.UserStatusInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 이용 상태")
public record UserStatusResponse(
        @Schema(description = "회원 ID", example = "42")
        Long id,

        @Schema(description = "역할. 정지는 역할을 바꾸지 않는다", example = "DEALER")
        Role role,

        @Schema(description = "이용 상태", example = "SUSPENDED")
        UserStatus status,

        @Schema(description = "정지 사유. 이용 중이면 null", nullable = true)
        String suspendReason
) {

    public static UserStatusResponse from(UserStatusInfo info) {
        return new UserStatusResponse(info.id(), info.role(), info.status(), info.suspendReason());
    }
}
