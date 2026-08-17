package com.softeer.race.user.application.dto.info;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserStatus;
import java.time.LocalDateTime;

/**
 * 관리자가 회원 한 명을 열었을 때 보는 정보. 연락 수단과 정지 사유가 여기에만 있다.
 * <p>
 * 비밀번호는 담지 않는다. 해시라도 응답으로 나갈 이유가 없다.
 */
public record UserDetailInfo(
        Long id,
        String username,
        String realName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        String suspendReason,
        LocalDateTime joinedAt
) {

    public static UserDetailInfo from(User user) {
        return new UserDetailInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getSuspendReason(),
                user.getCreatedAt());
    }
}
