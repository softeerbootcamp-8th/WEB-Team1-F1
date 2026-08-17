package com.softeer.race.user.application.dto.info;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserStatus;
import java.time.LocalDateTime;

/**
 * 관리자 회원 목록의 한 줄. 이메일 · 연락처 · 정지 사유는 담지 않는다 — 관리자가 실제로 열어 보는
 * 것은 목록 스무 건 중 한 건인데, 목록에 담으면 볼 일 없는 개인정보가 스무 건씩 응답으로 나간다
 * ({@code DealerApplicationSummaryInfo}가 사원증을 담지 않는 것과 같은 판단이다).
 * <p>
 * 이름을 가리지 않는다. 관리자 화면은 신고받은 사람을 특정하는 자리라 실명이 그대로 필요하고,
 * {@code MaskedName}은 회원끼리 서로를 보는 경매방 호가창을 위한 것이다.
 */
public record UserSummaryInfo(
        Long id,
        String username,
        String realName,
        Role role,
        UserStatus status,
        LocalDateTime joinedAt
) {

    public static UserSummaryInfo from(User user) {
        return new UserSummaryInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
