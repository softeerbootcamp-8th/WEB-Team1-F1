package com.softeer.race.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // 알림 발행이 식별자만 받으므로 엔티티를 읽지 않는다
    @Query("select u.id from User u where u.role = :role")
    List<Long> findIdsByRole(@Param("role") Role role);

    /**
     * 관리자 회원 검색. 세 조건 모두 선택이라 <b>null이면 그 조건을 건너뛴다</b> — 조건 조합마다
     * 메서드를 늘리는 대신 {@code :param is null}로 한 쿼리에 담는다.
     * <p>
     * 커서가 아니라 {@code Pageable}로 나눈다. 관리자 화면은 "정지 회원 12명" 같은 총 건수를
     * 보여줘야 하는데 커서로는 그 값을 줄 수 없고, 페이지를 건너뛰는 이동도 관리 도구에 필요하다.
     * 대가는 매 요청 count 쿼리 하나와 뒷페이지에서 커지는 offset인데, 관리자가 수백 페이지를
     * 넘길 일이 없어 실제로 치르지 않는다(경매 목록이 커서를 쓰는 이유와 반대되는 상황이다).
     * <p>
     * 검색어는 아이디 · 이름 · 연락처를 OR로 묶은 부분 일치다. 관리자는 신고를 받을 때 아이디를
     * 모르고 이름이나 번호만 아는 경우가 많다. <b>이 조건은 인덱스를 타지 못하고 전체를 훑는다</b> —
     * 앞이 열린 LIKE라 어떤 인덱스로도 좁힐 수 없다. 회원 수가 수만을 넘어가면 검색 전용 인덱스로
     * 옮겨야 한다.
     * <p>
     * count 쿼리를 직접 적어 준다. 자동 유도는 {@code select u}를 {@code select count(u)}로 바꾸는
     * 문자열 조작이라, 조건이 늘어난 이 쿼리에서 어긋나면 조용히 틀린 총 건수가 나간다.
     */
    @Query(value = """
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:keyword is null
                   or u.username like concat('%', :keyword, '%')
                   or u.realName like concat('%', :keyword, '%')
                   or u.phone like concat('%', :keyword, '%'))
            """,
            countQuery = """
            select count(u) from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:keyword is null
                   or u.username like concat('%', :keyword, '%')
                   or u.realName like concat('%', :keyword, '%')
                   or u.phone like concat('%', :keyword, '%'))
            """)
    Page<User> search(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("status") UserStatus status,
            Pageable pageable);
}
