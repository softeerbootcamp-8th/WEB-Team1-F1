package com.softeer.race.auction.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuctionStartAlertSubscriptionRepository
        extends JpaRepository<AuctionStartAlertSubscription, Long> {

    /**
     * 이 회원이 이 경매의 시작 알림을 신청했는지
     * <p>
     * 유니크 인덱스를 그대로 타고 행을 만들지 않는다. 발송에 성공하면 신청이 지워지므로,
     * 시작 뒤에는 신청했던 회원에게도 거짓이 돌아간다 — 시작 뒤에는 신청 화면 자체가 없다.
     */
    boolean existsByAuctionIdAndUserId(long auctionId, long userId);

    /**
     * 발송하거나 정리할 신청을 뽑는다
     * <p>
     * <b>예약 상태를 조회에서 걷어내는 이유.</b> 신청은 시작 전에만 되고 발송되면 지워지므로,
     * 남아 있는 신청의 대부분은 항상 아직 시작하지 않은 경매의 것이다. 상태를 뽑아 놓고 뒤에서
     * 가리면 상한이 보낼 것 없는 행으로 채워져 정작 보내야 할 신청이 뒤로 밀린다.
     * <p>
     * 회원은 식별자만 필요해 s.user.id 로 읽는다. 지연 연관의 식별자는 FK 컬럼에서 나오므로
     * 조인이 늘지 않는다. 정렬을 고정하는 것은 상한에 걸렸을 때 어느 신청이 먼저 나가는지를
     * 정해 두기 위한 것이다 — DB 가 정하게 두면 테스트를 쓸 수 없다.
     */
    @Query("""
            select new com.softeer.race.auction.domain.AuctionStartAlertTarget(
                s.id, a.id, s.user.id, a.status, v.model)
            from AuctionStartAlertSubscription s
            join s.auction a
            join a.post p
            join p.vehicle v
            where a.status in :statuses
            order by s.id
            """)
    List<AuctionStartAlertTarget> findTargets(
            @Param("statuses") Collection<AuctionStatus> statuses, Limit limit);
}