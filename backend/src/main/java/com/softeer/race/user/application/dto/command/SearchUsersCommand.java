package com.softeer.race.user.application.dto.command;

import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;

/**
 * 관리자 회원 검색의 입력. 검색어 · 역할 · 이용 상태는 모두 선택이고, null이면 그 조건을 걸지 않는다.
 *
 * @param keyword 아이디 · 이름 · 연락처에 걸리는 부분 일치 검색어
 * @param page    0부터 세는 페이지 번호
 */
public record SearchUsersCommand(
        String keyword,
        Role role,
        UserStatus status,
        int page
) {
}
