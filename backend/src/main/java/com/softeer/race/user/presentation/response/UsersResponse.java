package com.softeer.race.user.presentation.response;

import com.softeer.race.user.application.dto.info.UserPageInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 배열을 그대로 내려보내지 않고 객체로 감싼다 — {@code DealerApplicationsResponse}와 같은 이유고,
 * 여기서는 총 건수와 페이지 정보를 함께 실어야 해서 애초에 감싸는 것 말고는 방법이 없다.
 */
@Schema(description = "관리자 회원 목록")
public record UsersResponse(
        @Schema(description = "회원 목록. 가입 최신순")
        List<UserSummaryResponse> users,

        @Schema(description = "0부터 세는 현재 페이지 번호", example = "0")
        int page,

        @Schema(description = "전체 페이지 수. 조건에 맞는 회원이 없으면 0", example = "3")
        int totalPages,

        @Schema(description = "조건에 맞는 전체 회원 수", example = "47")
        long totalUsers
) {

    public static UsersResponse from(UserPageInfo info) {
        return new UsersResponse(
                info.users().stream().map(UserSummaryResponse::from).toList(),
                info.page(),
                info.totalPages(),
                info.totalUsers());
    }
}
