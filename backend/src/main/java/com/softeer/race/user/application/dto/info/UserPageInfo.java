package com.softeer.race.user.application.dto.info;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 한 페이지치 회원과 그 페이지가 전체 어디쯤인지.
 * <p>
 * {@code Page<UserSummaryInfo>}를 그대로 넘기지 않는다. 그러면 Spring Data 타입이 프레젠테이션까지
 * 올라오고, {@code Page}는 응답으로 직렬화하면 안 쓰는 필드가 열 개 넘게 딸려 나온다.
 * 화면이 실제로 쓰는 네 값만 옮긴다.
 *
 * @param page 0부터 센다. Spring Data의 셈법을 그대로 쓰고, 1부터 세는 화면 표기는 프론트가 맡는다
 */
public record UserPageInfo(
        List<UserSummaryInfo> users,
        int page,
        int totalPages,
        long totalUsers
) {

    public static UserPageInfo from(Page<UserSummaryInfo> page) {
        return new UserPageInfo(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements());
    }
}
