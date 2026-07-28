package com.softeer.race.user.application.dto.command;

import com.softeer.race.user.domain.Role;

/**
 * 회원가입 유스케이스의 입력. presentation의 SignUpRequest를 대신 받아 application이 HTTP 계층을 모르게 한다.
 * 형식 검증과 Swagger 문서화는 SignUpRequest의 책임이므로 여기에는 애너테이션을 두지 않는다.
 */
public record SignUpCommand(
        String username,
        String email,
        // 평문 비밀번호, 해싱은 UserService가 PasswordEncoder로 처리한다
        String password,
        String realName,
        String phone,
        String address,
        Role role
) {
}
