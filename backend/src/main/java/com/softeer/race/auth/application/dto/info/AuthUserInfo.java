package com.softeer.race.auth.application.dto.info;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;

/**
 * 인증 유스케이스가 내보내는 회원 정보.
 * SignUpInfo와 같은 원칙으로 password와 phone을 담지 않는 것이 응답 비노출 보장선이므로
 * 필드를 늘리지 않는다.
 */
public record AuthUserInfo(
        Long id,
        String username,
        String email,
        String realName,
        Role role
) {

    public static AuthUserInfo from(User user) {
        return new AuthUserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getRole());
    }
}
