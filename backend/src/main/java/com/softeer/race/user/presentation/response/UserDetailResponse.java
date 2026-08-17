package com.softeer.race.user.presentation.response;

import com.softeer.race.user.application.dto.info.UserDetailInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "회원 상세")
public record UserDetailResponse(
        @Schema(description = "회원 ID", example = "42")
        Long id,

        @Schema(description = "아이디", example = "race_kim")
        String username,

        @Schema(description = "이름", example = "김레이스")
        String realName,

        @Schema(description = "이메일", example = "race@race.kr")
        String email,

        @Schema(description = "휴대전화 번호", example = "01012345678")
        String phone,

        @Schema(description = "역할", example = "DEALER")
        Role role,

        @Schema(description = "이용 상태", example = "SUSPENDED")
        UserStatus status,

        @Schema(description = "정지 사유. 이용 중이면 null", nullable = true)
        String suspendReason,

        @Schema(description = "가입 일시")
        LocalDateTime joinedAt
) {

    public static UserDetailResponse from(UserDetailInfo info) {
        return new UserDetailResponse(
                info.id(), info.username(), info.realName(), info.email(), info.phone(),
                info.role(), info.status(), info.suspendReason(), info.joinedAt());
    }
}
