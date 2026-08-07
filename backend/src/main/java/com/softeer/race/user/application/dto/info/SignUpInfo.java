package com.softeer.race.user.application.dto.info;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;

/**
 * 회원가입 유스케이스의 결과. 엔티티를 presentation으로 넘기지 않기 위한 경계 역할을 한다.
 * password와 phone을 담지 않는 것이 응답 비노출 보장선이므로 필드를 늘리지 않는다.
 */
public record SignUpInfo(
        Long id,
        String username,
        String email,
        String realName,
        Role role
) {

    public static SignUpInfo from(User user) {
        return new SignUpInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getRole());
    }
}
