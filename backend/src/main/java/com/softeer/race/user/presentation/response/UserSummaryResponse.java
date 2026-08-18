package com.softeer.race.user.presentation.response;

import com.softeer.race.user.application.dto.info.UserSummaryInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "회원 목록의 한 줄")
public record UserSummaryResponse(
        @Schema(description = "회원 ID", example = "42")
        Long id,

        @Schema(description = "아이디", example = "race_kim")
        String username,

        @Schema(description = "이름", example = "김레이스")
        String realName,

        @Schema(description = "역할", example = "DEALER")
        Role role,

        @Schema(description = "이용 상태", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "가입 일시")
        LocalDateTime joinedAt
) {

    public static UserSummaryResponse from(UserSummaryInfo info) {
        return new UserSummaryResponse(
                info.id(), info.username(), info.realName(),
                info.role(), info.status(), info.joinedAt());
    }
}
