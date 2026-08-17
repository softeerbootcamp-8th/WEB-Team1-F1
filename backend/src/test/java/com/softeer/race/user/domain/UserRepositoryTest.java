package com.softeer.race.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 검색을 실제 MySQL 에서
 * <p>
 * 1. 선택 조건
 * 검색어 · 역할 · 이용 상태가 각각 null 일 때 그 조건만 빠지는지. 한 쿼리에
 * {@code :param is null} 로 담았으므로 조합마다 결과가 갈리는지 확인해야 한다
 * <p>
 * 2. 검색 대상
 * 아이디 · 이름 · 연락처 셋 중 어디에 걸려도 찾아지는지. OR 묶음의 우선순위가 잘못 잡히면
 * 필터가 무시된 채 검색어만으로 걸린다
 * <p>
 * 3. 페이징
 * 총 건수와 페이지 수가 조건을 반영하는지. count 쿼리를 따로 적어 두어 본문과 어긋날 수 있다
 */
@DisplayName("관리자 회원 검색 테스트")
@Transactional
class UserRepositoryTest extends IntegrationTestSupport {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "id");
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, LATEST_FIRST);

    @Autowired
    private UserRepository userRepository;

    private User kim;
    private User lee;
    private User park;
    private User admin;

    @BeforeEach
    void setUp() {
        kim = save("race_kim", "김레이스", "01011112222", Role.GENERAL);
        lee = save("auto_lee", "이딜러", "01033334444", Role.DEALER);
        park = save("park_auto", "박정지", "01055556666", Role.DEALER);
        park.suspend("허위 매물을 반복 등록했습니다.");
        admin = save("admin01", "관리자", "01077778888", Role.ADMIN);
    }

    // ================= 선택 조건 =================

    @Test
    @DisplayName("조건이 모두 비면 전체 회원이 가입 최신순으로 나온다")
    void searchWithoutConditions() {
        Page<User> page = userRepository.search(null, null, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getId)
                .containsExactly(admin.getId(), park.getId(), lee.getId(), kim.getId());
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("역할만 주면 그 역할의 회원만 나온다")
    void searchByRole() {
        Page<User> page = userRepository.search(null, Role.DEALER, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername)
                .containsExactly("park_auto", "auto_lee");
    }

    @Test
    @DisplayName("이용 상태만 주면 그 상태의 회원만 나온다")
    void searchByStatus() {
        Page<User> page = userRepository.search(null, null, UserStatus.SUSPENDED, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("park_auto");
    }

    // 두 조건이 and 로 묶이지 않으면 정지되지 않은 딜러까지 함께 나온다
    @Test
    @DisplayName("역할과 이용 상태는 함께 좁힌다")
    void searchByRoleAndStatus() {
        Page<User> page = userRepository.search(null, Role.DEALER, UserStatus.ACTIVE, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("auto_lee");
    }

    // ================= 검색 대상 =================

    @Test
    @DisplayName("검색어가 아이디의 일부에 걸린다")
    void searchByUsernameFragment() {
        Page<User> page = userRepository.search("_kim", null, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("race_kim");
    }

    // 관리자는 신고를 받을 때 아이디를 모르고 이름만 아는 경우가 많다
    @Test
    @DisplayName("검색어가 이름의 일부에 걸린다")
    void searchByRealNameFragment() {
        Page<User> page = userRepository.search("정지", null, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("park_auto");
    }

    @Test
    @DisplayName("검색어가 연락처의 일부에 걸린다")
    void searchByPhoneFragment() {
        Page<User> page = userRepository.search("3333", null, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("auto_lee");
    }

    @Test
    @DisplayName("아이디와 이름 여러 건에 걸리면 모두 나온다")
    void searchMatchesAcrossColumns() {
        // auto_lee 는 아이디에, park_auto 도 아이디에 걸린다
        Page<User> page = userRepository.search("auto", null, null, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername)
                .containsExactly("park_auto", "auto_lee");
    }

    /*
     * OR 묶음에 괄호가 빠지면 and 가 먼저 묶여, 검색어에 걸린 회원이 역할 필터를 무시한 채
     * 함께 나온다. 이 조합이 그 실수를 잡는다
     */
    @Test
    @DisplayName("검색어와 역할 필터는 함께 좁힌다")
    void searchByKeywordAndRole() {
        Page<User> page = userRepository.search("auto", Role.DEALER, UserStatus.SUSPENDED, FIRST_PAGE);

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("park_auto");
    }

    @Test
    @DisplayName("아무에게도 걸리지 않는 검색어는 빈 목록과 0건을 준다")
    void searchWithoutMatch() {
        Page<User> page = userRepository.search("없는사람", null, null, FIRST_PAGE);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    // ================= 페이징 =================

    // count 쿼리를 본문과 따로 적어 두어, 조건이 반영되지 않으면 총 건수만 조용히 틀린다
    @Test
    @DisplayName("총 건수와 페이지 수는 조건을 반영한다")
    void pagingCountsMatchCondition() {
        Pageable twoPerPage = PageRequest.of(0, 2, LATEST_FIRST);

        Page<User> page = userRepository.search(null, Role.DEALER, null, twoPerPage);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 페이지는 앞 페이지에 담기지 않은 회원을 잇는다")
    void pagingReadsNextSlice() {
        Pageable secondPage = PageRequest.of(1, 2, LATEST_FIRST);

        Page<User> page = userRepository.search(null, null, null, secondPage);

        assertThat(page.getContent()).extracting(User::getId)
                .containsExactly(lee.getId(), kim.getId());
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    private User save(String username, String realName, String phone, Role role) {
        return userRepository.save(User.create(
                username, username + "@race.kr", "$2a$10$encoded", realName, phone, role));
    }
}
