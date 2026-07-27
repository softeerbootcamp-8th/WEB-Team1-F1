package com.softeer.race.user.presentation.response;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignUpResponse(
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

    public static SignUpResponse from(User user) {
        return new SignUpResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getRole());
    }
}
