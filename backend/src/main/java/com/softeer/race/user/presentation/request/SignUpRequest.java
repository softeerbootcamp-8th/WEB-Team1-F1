package com.softeer.race.user.presentation.request;

import com.softeer.race.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignUpRequest(
        @Schema(description = "이메일", example = "race@race.kr")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "비밀번호", example = "password123")
        @NotBlank
        @Size(min = 8, max = 64)
        String password,

        @Schema(description = "실명", example = "김레이스")
        @NotBlank
        @Size(min = 2, max = 30)
        String realName,

        @Schema(description = "휴대전화 번호", example = "010-1234-5678")
        @NotBlank
        @Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$")
        String phone,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        @NotBlank
        @Size(max = 255)
        String address,

        @Schema(description = "회원 유형", example = "GENERAL")
        @NotNull
        Role role
) {
}
