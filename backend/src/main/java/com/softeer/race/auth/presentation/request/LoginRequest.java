package com.softeer.race.auth.presentation.request;

import com.softeer.race.auth.application.dto.command.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청.
 * <p>
 * SignUpRequest의 @Pattern이나 @Size(min)을 복사하지 않는다. 로그인에 형식 검증을 걸면 400 응답이
 * 비밀번호 정책을 노출하고, 정책을 바꾸기 전에 만든 계정이 로그인할 수 없게 되며,
 * "형식 오류는 400 / 자격 오류는 401"로 응답이 갈려 공격자에게 판별 수단을 준다.
 * 상한만 남기는데, bcrypt 검증이 비싸서 길이 제한 없는 입력은 그 자체가 DoS 벡터다.
 */
@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "race_kim")
        @NotBlank
        @Size(max = 20)
        String username,

        @Schema(description = "비밀번호", example = "password123")
        @NotBlank
        @Size(max = 64)
        String password
) {

    public LoginCommand toCommand() {
        return new LoginCommand(username, password);
    }
}
