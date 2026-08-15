package com.softeer.race.user.presentation.request;

import com.softeer.race.user.application.dto.command.SignUpCommand;
import com.softeer.race.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignUpRequest(
        // 대소문자만 다른 아이디가 서로 다른 계정이 되는 혼동을 막기 위해 소문자로만 받는다
        @Schema(description = "로그인 아이디", example = "race_kim")
        @NotBlank
        @Pattern(regexp = "^[a-z0-9_]{4,20}$")
        String username,

        @Schema(description = "이메일", example = "race@race.kr")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        // bcrypt는 UTF-8 72바이트를 넘는 입력에 예외를 던진다
        // 출력 가능 ASCII로 제한해 1자 = 1바이트를 보장하므로 아래 max는 72를 넘길 수 없다
        @Schema(description = "비밀번호(공백을 제외한 ASCII 문자)", example = "password123")
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "비밀번호는 공백을 제외한 영문, 숫자, 특수문자만 사용할 수 있습니다.")
        String password,

        @Schema(description = "실명", example = "김레이스")
        @NotBlank
        @Size(min = 2, max = 30)
        String realName,

        // 같은 번호가 하이픈 유무로 두 형식이 되면 중복 검사가 무너진다
        // 저장 형식을 숫자 11자리 하나로 고정해 uk_users_phone이 실제 중복을 잡게 한다
        @Schema(description = "휴대전화 번호(숫자만)", example = "01012345678")
        @NotBlank
        @Pattern(regexp = "^010\\d{8}$",
                message = "휴대전화 번호는 '-' 없이 010으로 시작하는 숫자 11자리로 입력해야 합니다.")
        String phone,

        @Schema(description = "회원 유형", example = "GENERAL")
        @NotNull
        Role role,

        @Schema(description = "딜러 회원가입 전에 업로드한 자동차매매사원증 객체 키",
                example = "dealer-licenses/2026/08/3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.jpg",
                nullable = true)
        @Size(max = 255)
        String dealerLicenseKey
) {

    public SignUpCommand toCommand() {
        return new SignUpCommand(username, email, password, realName, phone, role, dealerLicenseKey);
    }
}
