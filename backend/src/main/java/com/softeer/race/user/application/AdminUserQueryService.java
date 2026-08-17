package com.softeer.race.user.application;

import static com.softeer.race.user.exception.UserErrorCode.NOT_FOUND;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.application.dto.command.SearchUsersCommand;
import com.softeer.race.user.application.dto.info.UserDetailInfo;
import com.softeer.race.user.application.dto.info.UserPageInfo;
import com.softeer.race.user.application.dto.info.UserSummaryInfo;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 회원 조회 유스케이스.
 * <p>
 * 이용정지를 다루는 {@link UserSuspensionService}와 나눠 둔다. 조회는 아무것도 바꾸지 않고 지킬
 * 규칙도 없어서, 한 클래스에 두면 정지의 검증 규칙 사이에 조회 메서드가 끼어 읽힌다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService {

    /**
     * 한 페이지에 담는 회원 수. 요청이 정하지 못하게 서버가 고정한다 — 열어 두면 한 번의 요청으로
     * 회원 전체를 개인정보째 긁어 갈 수 있고, 관리 화면에 그만한 자유도가 필요하지도 않다.
     */
    private static final int PAGE_SIZE = 20;

    /** 가입 최신순. id 는 IDENTITY 라 가입 순서와 같고, 시각 컬럼과 달리 값이 겹치지 않아 순서가 흔들리지 않는다 */
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final UserRepository userRepository;

    /**
     * 조건에 맞는 회원을 한 페이지씩. 세 조건 모두 비어 있으면 전체 회원이 최신순으로 나온다.
     * <p>
     * 빈 검색어를 null로 바꿔 넘긴다. 검색창을 비운 채 요청하면 {@code keyword=}가 그대로 실려 오는데,
     * 그대로 두면 <b>"빈 문자열을 포함하는" 조건</b>이 되어 조건을 건 것도 안 건 것도 아닌 쿼리가 된다.
     */
    public UserPageInfo search(SearchUsersCommand command) {
        Pageable pageable = PageRequest.of(command.page(), PAGE_SIZE, LATEST_FIRST);

        return UserPageInfo.from(userRepository
                .search(blankToNull(command.keyword()), command.role(), command.status(), pageable)
                .map(UserSummaryInfo::from));
    }

    public UserDetailInfo findDetail(Long userId) {
        return userRepository.findById(userId)
                .map(UserDetailInfo::from)
                .orElseThrow(() -> new BusinessException(NOT_FOUND));
    }

    private static String blankToNull(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.strip();
    }
}
