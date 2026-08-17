package com.softeer.race.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.application.dto.command.SearchUsersCommand;
import com.softeer.race.user.application.dto.info.UserDetailInfo;
import com.softeer.race.user.application.dto.info.UserPageInfo;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.user.domain.UserStatus;
import com.softeer.race.user.exception.UserErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 회원 조회")
class AdminUserQueryServiceTest {

    private static final long USER_ID = 42L;
    private static final int PAGE_SIZE = 20;

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private AdminUserQueryService adminUserQueryService;

    @BeforeEach
    void setUp() {
        adminUserQueryService = new AdminUserQueryService(userRepository);
    }

    @Test
    @DisplayName("검색 결과에 총 건수와 페이지 정보가 함께 실린다")
    void searchCarriesPageInfo() {
        // 21번째 한 명만 남은 둘째 페이지, 페이지 크기가 20이라 전체는 두 페이지다
        givenFound(List.of(user(Role.DEALER)), PageRequest.of(1, PAGE_SIZE), 21);

        UserPageInfo info = adminUserQueryService.search(
                new SearchUsersCommand(null, Role.DEALER, null, 1));

        assertThat(info.page()).isEqualTo(1);
        assertThat(info.totalUsers()).isEqualTo(21);
        assertThat(info.totalPages()).isEqualTo(2);
        assertThat(info.users()).singleElement()
                .satisfies(summary -> assertThat(summary.role()).isEqualTo(Role.DEALER));
    }

    /*
     * 검색창을 비운 채 요청하면 keyword= 가 그대로 실려 온다. 빈 문자열을 그대로 넘기면
     * "빈 문자열을 포함하는" 조건이 되어 조건을 건 것도 안 건 것도 아닌 쿼리가 된다
     */
    @DisplayName("비어 있는 검색어는 조건에서 빠진다")
    @ParameterizedTest(name = "검색어 \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankKeywordBecomesNoCondition(String keyword) {
        givenFound(List.of(), PageRequest.of(0, PAGE_SIZE), 0);

        adminUserQueryService.search(new SearchUsersCommand(keyword, null, null, 0));

        verify(userRepository).search(isNull(), isNull(), isNull(), any());
    }

    // 앞뒤 공백까지 넣어 찾으면 아무에게도 걸리지 않는다
    @Test
    @DisplayName("검색어의 앞뒤 공백은 털어 낸다")
    void keywordIsStripped() {
        givenFound(List.of(), PageRequest.of(0, PAGE_SIZE), 0);

        adminUserQueryService.search(new SearchUsersCommand("  race_kim  ", null, null, 0));

        verify(userRepository).search(eq("race_kim"), isNull(), isNull(), any());
    }

    // 요청이 페이지 크기를 정하면 한 번의 요청으로 회원 전체를 개인정보째 긁어 갈 수 있다
    @Test
    @DisplayName("페이지 크기는 서버가 고정하고 가입 최신순으로 읽는다")
    void pageSizeAndSortAreFixed() {
        givenFound(List.of(), PageRequest.of(3, PAGE_SIZE), 0);

        adminUserQueryService.search(new SearchUsersCommand(null, null, null, 3));

        verify(userRepository).search(isNull(), isNull(), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(PAGE_SIZE);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageableCaptor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    @DisplayName("상세에는 목록에 없는 연락 수단과 정지 사유가 담긴다")
    void findDetailCarriesContactAndReason() {
        User user = user(Role.GENERAL);
        user.suspend("허위 매물을 반복 등록했습니다.");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserDetailInfo info = adminUserQueryService.findDetail(USER_ID);

        assertThat(info.email()).isEqualTo("race@race.kr");
        assertThat(info.phone()).isEqualTo("01012345678");
        assertThat(info.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(info.suspendReason()).isEqualTo("허위 매물을 반복 등록했습니다.");
    }

    @Test
    @DisplayName("없는 회원의 상세를 열면 404다")
    void findDetailRejectsMissingUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserQueryService.findDetail(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.NOT_FOUND));
    }

    private void givenFound(List<User> users, Pageable pageable, long total) {
        Page<User> page = new PageImpl<>(users, pageable, total);
        when(userRepository.search(any(), any(), any(), any())).thenReturn(page);
    }

    private static User user(Role role) {
        User user = User.create("race_kim", "race@race.kr", "$2a$10$encoded",
                "김레이스", "01012345678", role);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }
}
