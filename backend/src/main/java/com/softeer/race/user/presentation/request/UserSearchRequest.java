package com.softeer.race.user.presentation.request;

import com.softeer.race.user.application.dto.command.SearchUsersCommand;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 회원 목록 조회 조건. 전부 선택 파라미터고, 비어 있으면 그 조건을 걸지 않는다
 * ({@code AuctionListFilterRequest}와 같은 형태다).
 */
public record UserSearchRequest(
        @Schema(description = "아이디 · 이름 · 연락처에 걸리는 부분 일치 검색어", example = "race_kim")
        String keyword,

        @Schema(description = "역할. 없으면 전체", example = "DEALER")
        Role role,

        @Schema(description = "이용 상태. 없으면 전체", example = "SUSPENDED")
        UserStatus status,

        // 음수를 그대로 넘기면 PageRequest 가 IllegalArgumentException 을 던져 500 이 된다,
        // 잘못된 요청은 400 으로 드러나야 한다
        @Schema(description = "0부터 세는 페이지 번호. 없으면 첫 페이지", example = "0")
        @PositiveOrZero Integer page
) {

    private static final int FIRST_PAGE = 0;

    public SearchUsersCommand toCommand() {
        return new SearchUsersCommand(
                keyword, role, status, page == null ? FIRST_PAGE : page);
    }
}
