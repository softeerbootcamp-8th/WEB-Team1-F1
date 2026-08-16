package com.softeer.race.dealer.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealerApplicationRepository extends JpaRepository<DealerApplication, Long> {

    /**
     * 심사 중인 신청이 이미 있는지. 재신청이 열려도 이 판정은 그대로다 —
     * 대기 중에는 막고 결론이 난 뒤에는 새 신청을 허용한다.
     */
    boolean existsByApplicantIdAndStatus(Long applicantId, DealerApplicationStatus status);

    /**
     * 관리자 심사 목록. 나누어 읽지 않고 전량을 접수 순으로 돌려준다.
     * <p>
     * 페이징을 붙이지 않은 것은 대기 건수가 한 화면을 크게 넘길 서비스가 아니기 때문이다. 그런데도
     * {@code join fetch}는 붙인다 — 목록이 신청자 이름을 함께 보여주므로, 없으면 건수만큼
     * 지연 로딩 쿼리가 따라 나간다.
     */
    @Query("select a from DealerApplication a join fetch a.applicant"
            + " where a.status = :status order by a.id")
    List<DealerApplication> findAllByStatus(@Param("status") DealerApplicationStatus status);

    /**
     * 상세 조회. 신청자를 함께 읽어 온다 — 화면이 신청자 정보를 보여주고, 이쪽은 잠그지 않으므로
     * {@code join fetch}로 쿼리를 하나로 줄일 수 있다.
     */
    @Query("select a from DealerApplication a join fetch a.applicant where a.id = :id")
    Optional<DealerApplication> findDetailById(@Param("id") Long id);

    /**
     * 판정 직전에 신청 행을 잠근다.
     * <p>
     * 잠그지 않으면 관리자 둘이 같은 신청을 동시에 열었을 때 둘 다 {@code PENDING}을 읽고 통과한 뒤
     * 나중 쓰기가 앞의 판정을 덮는다. 승인이 반려를 덮으면 반려 사유가 남은 채 딜러가 되고,
     * 반려가 승인을 덮으면 이미 승격된 역할은 그대로 남는다.
     * <p>
     * {@code EvaluationRepository.findByIdForUpdate}와 같은 이유로 {@code join fetch}를 붙이지
     * 않는다. 조인된 회원 행까지 잠겨 잠금 범위가 넓어진다. 그래서 승인이 신청자를 만질 때
     * 프록시 초기화 쿼리가 한 번 더 나가고, 그 접근은 반드시 트랜잭션 안이어야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DealerApplication a where a.id = :id")
    Optional<DealerApplication> findByIdForUpdate(@Param("id") Long id);
}
