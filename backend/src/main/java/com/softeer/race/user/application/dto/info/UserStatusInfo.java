package com.softeer.race.user.application.dto.info;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserStatus;

/**
 * 정지 · 해제 직후의 회원 이용 상태. 관리자가 방금 누른 것이 반영됐는지 확인할 만큼만 담는다.
 * <p>
 * 역할을 함께 담는 이유는 정지가 역할을 건드리지 않는다는 것을 응답에서도 보이게 하려는 것이다 —
 * 정지된 딜러는 여전히 DEALER 로 돌아온다.
 */
public record UserStatusInfo(
        Long id,
        Role role,
        UserStatus status,
        String suspendReason
) {

    public static UserStatusInfo from(User user) {
        return new UserStatusInfo(
                user.getId(), user.getRole(), user.getStatus(), user.getSuspendReason());
    }
}
