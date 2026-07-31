package com.softeer.race.auth.presentation.response;

import com.softeer.race.auth.application.dto.info.AuthUserInfo;
import com.softeer.race.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인과 내 정보 조회가 함께 쓰는 응답. 세션 토큰을 담을 필드가 없는 것이 설계다. */
@Schema(description = "인증된 회원 정보")
public record AuthUserResponse(
        @Schema(description = "회원 ID", example = "1")
        Long id,

        @Schema(description = "로그인 아이디", example = "race_kim")
        String username,

        @Schema(description = "이메일", example = "race@race.kr")
        String email,

        @Schema(description = "실명", example = "김레이스")
        String realName,

        @Schema(description = "회원 유형", example = "GENERAL")
        Role role
) {

    public static AuthUserResponse from(AuthUserInfo info) {
        return new AuthUserResponse(
                info.id(),
                info.username(),
                info.email(),
                info.realName(),
                info.role());
    }
}
