package com.softeer.race.evaluation.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    /**
     * 같은 번호판으로 새 방문견적을 막아야 하는 신청이 있는지.
     * <p>
     * REQUESTED는 방문견적이 진행 중이므로 항상 막는다. APPROVED는 진단 뒤 출품 흐름이 남아 있어
     * 기본적으로 막되, 해당 차량의 경매가 낙찰 종료(ENDED)됐다면 판매 흐름 전체가 끝난 것이므로
     * 다시 신청할 수 있다. 유찰(FAILED)은 같은 진단 차량으로 재출품할 수 있어 새 방문견적을 막는다.
     * <p>
     * 판정 기준은 vehicle_id가 아니라 번호판이다. 방문견적을 신청할 때마다 vehicle 행이 새로 생기고,
     * 같은 차는 판매가 끝난 뒤 다시 등록될 수 있어 번호판에 unique 제약을 둘 수 없기 때문이다.
     */
    @Query("""
            select count(e) > 0
            from Evaluation e
            where e.vehicle.plateNumber = :plateNumber
                and (
                    e.status = com.softeer.race.evaluation.domain.EvaluationStatus.REQUESTED
                    or (
                        e.status = com.softeer.race.evaluation.domain.EvaluationStatus.APPROVED
                        and not exists (
                            select a.id
                            from Auction a
                            where a.post.vehicle = e.vehicle
                                and a.status = com.softeer.race.auction.domain.AuctionStatus.ENDED
                        )
                    )
                )
            """)
    boolean existsBlockingVisitQuoteByPlateNumber(@Param("plateNumber") String plateNumber);

    /**
     * 아직 아무도 수락하지 않은 신청들. 방문일이 임박한 순서로 나온다.
     * <p>
     * 조건이 {@code status = REQUESTED}와 {@code evaluator is null} 두 개다. 상태만으로는 이미
     * 배정된 신청까지 걸려 나오고, evaluator만으로는 평가가 끝난 뒤 배정이 지워지는 흐름이 생길 때
     * 종료된 건이 섞인다. 둘을 함께 두는 것이 {@code Evaluation.evaluator}의 주석이 말하는
     * "REQUESTED + evaluator == null이 배정 대기"의 정의 그대로다.
     * <p>
     * vehicle을 join fetch 한다. 목록의 각 항목이 번호판과 제원을 보여주므로 없으면 건수만큼
     * 지연 로딩 쿼리가 더 나간다.
     * <p>
     * 페이징을 두지 않는다. 배정되는 즉시 빠지는 목록이라 규모의 상한이 낮고, 근거 있는 페이지
     * 크기를 정할 수 없어 임의의 숫자가 된다. 필요해지면 AuctionListService의 커서 방식을 따른다.
     */
    @Query("""
            select e
            from Evaluation e
            join fetch e.vehicle
            where e.status = :requested
                and e.evaluator is null
            order by e.visitDate, e.id
            """)
    List<Evaluation> findAssignable(@Param("requested") EvaluationStatus requested);

    /**
     * 이 판매자가 낸 신청들. 최신 접수부터 나온다.
     * <p>
     * {@code v.seller.id}는 vehicle 행에 있는 FK 컬럼만 읽어 users 조인이 생기지 않는다.
     * {@code v.seller.username}처럼 다른 필드를 건드리면 그때 조인이 늘어난다.
     * <p>
     * {@code createdAt}이 아니라 id로 정렬한다. 같은 순서인데 PK 인덱스를 그대로 쓴다.
     * <p>
     * vehicle을 join fetch 한다. 목록의 각 항목이 번호판과 제원을 보여주므로 없으면 건수만큼
     * 지연 로딩 쿼리가 더 나간다.
     */
    @Query("""
            select e
            from Evaluation e
            join fetch e.vehicle v
            where v.seller.id = :sellerId
            order by e.id desc
            """)
    List<Evaluation> findBySellerId(@Param("sellerId") long sellerId);

    /**
     * 이 평가사가 맡은 신청들. 방문일이 임박한 순이다 — 평가사에게 급한 것은 언제 어디를
     * 가야 하는가이고, {@link #findAssignable}도 같은 기준으로 정렬한다.
     */
    @Query("""
            select e
            from Evaluation e
            join fetch e.vehicle v
            where e.evaluator.id = :evaluatorId
            order by e.visitDate, e.id
            """)
    List<Evaluation> findByEvaluatorId(@Param("evaluatorId") long evaluatorId);

    /**
     * 상세 조회용. 차량 제원과 담당 평가사를 함께 보여주므로 둘 다 붙여 읽는다.
     * <p>
     * {@code findById}로 대신하지 않는다. 그쪽은 vehicle과 evaluator가 프록시로 남아 상세를
     * 조립하는 동안 지연 로딩 쿼리가 두 번 더 나간다.
     * <p>
     * evaluator는 <b>left join</b>이다. 배정 전에는 비어 있어 inner join으로 붙이면 아직 아무도
     * 수락하지 않은 신청이 조회 결과에서 통째로 사라진다 — 판매자가 접수 직후 상세를 열 수 없게 된다.
     */
    @Query("""
            select e
            from Evaluation e
            join fetch e.vehicle v
            left join fetch e.evaluator
            where e.id = :evaluationId
            """)
    Optional<Evaluation> findWithVehicleById(@Param("evaluationId") long evaluationId);

    /**
     * 배정을 위해 신청 한 건을 잠그고 읽는다.
     * <p>
     * 잠금 없이 처리하면 두 평가사가 같은 {@code evaluator == null}을 읽고 둘 다 통과한 뒤 둘 다
     * 쓴다. 나중 쓰기가 앞의 배정을 덮어써서 "먼저 수락한 한 명"이 지켜지지 않는다.
     * <p>
     * AuctionRepository.findByIdForUpdate와 같은 이유로 join fetch를 붙이지 않는다. FOR UPDATE에
     * 조인이 걸리면 차량 행까지 잠겨 잠금 범위를 신청 한 건으로 제한한 의미가 없어진다.
     * 그래서 배정 응답에 쓰는 번호판은 지연 로딩 쿼리 한 번을 더 낸다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evaluation e where e.id = :evaluationId")
    Optional<Evaluation> findByIdForUpdate(@Param("evaluationId") long evaluationId);
}
