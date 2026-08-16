package com.softeer.race.user.application.dto.info;

import com.softeer.race.dealer.domain.DealerApplicationStatus;
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
        Role role,
        /**
         * 함께 접수된 딜러 심사 신청의 상태. 딜러로 신청하지 않았으면 null이다.
         * <p>
         * 딜러로 신청해도 {@code role}은 GENERAL로 내려간다. 이 필드가 없으면 클라이언트는 자기 요청이
         * 심사로 접수된 것인지 딜러 선택이 무시된 것인지 구분하지 못한다.
         */
        DealerApplicationStatus dealerApplicationStatus
) {

    public static SignUpInfo from(User user) {
        return from(user, null);
    }

    public static SignUpInfo from(User user, DealerApplicationStatus dealerApplicationStatus) {
        return new SignUpInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getRole(),
                dealerApplicationStatus);
    }
}
