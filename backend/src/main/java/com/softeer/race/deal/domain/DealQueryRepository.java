package com.softeer.race.deal.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;

/**
 * 거래 화면에 필요한 조회
 * <p>
 * 쓰기가 없어 Repository 로 좁힌다. 조회 조건에 당사자를 넣어, 남의 거래는 없는 것으로 취급한다 —
 * 권한 없음으로 답하면 그 번호의 거래가 존재한다는 사실이 새어 나간다.
 */
public interface DealQueryRepository extends Repository<Deal, Long> {

    /**
     * 내가 당사자인 거래 한 페이지, 최근에 만들어진 것부터
     * <p>
     * 단계 변경 시각이 아니라 식별자로 페이징한다. 그 값은 거래가 진행되면 바뀌어서,
     * 페이지 사이에 순서가 뒤집히면 같은 거래가 두 번 보이거나 통째로 건너뛴다.
     * <p>
     * 삭제된 경매글을 거르지 않는다. 판매자가 글을 내려도 이미 성사된 거래는 남아야 한다.
     */
    @Query("""
            select new com.softeer.race.deal.domain.DealListRow(
                d.id, d.status, d.finalPrice, d.statusChangedAt,
                a.id, v.model, v.mainPhotoUrl,
                s.id, case when s.id = :userId then b.realName else s.realName end)
            from Deal d
            join d.auction a
            join a.post p
            join p.vehicle v
            join d.seller s
            join d.buyer b
            where (s.id = :userId or b.id = :userId)
                and d.id < :cursor
            order by d.id desc
            """)
    List<DealListRow> findPage(@Param("userId") long userId,
                               @Param("cursor") long cursor,
                               Limit limit);
    /**
     * 내가 당사자인 거래 하나
     * <p>
     * 남의 거래는 조건에서 걸러 없는 것과 같게 만든다. 권한 없음으로 답하면 그 번호의 거래가
     * 존재한다는 사실이 새어 나가고, 번호를 훑으면 전체 거래 수를 추정할 수 있다.
     */
    @Query("""
            select new com.softeer.race.deal.domain.DealDetailRow(
                d.id, d.status, d.finalPrice, d.statusChangedAt, d.createdAt, d.cancellationReason,
                a.id, v.model, v.modelYear, v.mileage, v.mainPhotoUrl,
                s.id, case when s.id = :userId then b.realName else s.realName end)
            from Deal d
            join d.auction a
            join a.post p
            join p.vehicle v
            join d.seller s
            join d.buyer b
            where d.id = :dealId
                and (s.id = :userId or b.id = :userId)
            """)
    Optional<DealDetailRow> findDetail(@Param("dealId") long dealId, @Param("userId") long userId);
}