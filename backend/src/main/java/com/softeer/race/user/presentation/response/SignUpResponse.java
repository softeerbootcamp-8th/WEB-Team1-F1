package com.softeer.race.user.presentation.response;

import com.softeer.race.dealer.domain.DealerApplicationStatus;
import com.softeer.race.user.application.dto.info.SignUpInfo;
import com.softeer.race.user.domain.Role;
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

        @Schema(description = "회원 유형. 딜러로 신청해도 승인 전까지는 GENERAL 이다", example = "GENERAL")
        Role role,

        @Schema(description = "함께 접수된 딜러 심사 신청의 상태. 딜러로 신청하지 않았으면 null",
                example = "PENDING", nullable = true)
        DealerApplicationStatus dealerApplicationStatus
) {

    public static SignUpResponse from(SignUpInfo info) {
        return new SignUpResponse(
                info.id(),
                info.username(),
                info.email(),
                info.realName(),
                info.role(),
                info.dealerApplicationStatus());
    }
}
