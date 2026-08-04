package com.softeer.race.auction.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    /**
     * 해당 차량에 주어진 상태의 경매가 이미 있는지 확인한다
     */
    @Query("""
            select count(a) > 0
            from Auction a
            where a.post.vehicle.id = :vehicleId
            and a.status in :statuses
            """
    )
    boolean existsActiveByVehicleId(@Param("vehicleId") Long vehicleId, @Param("statuses") Collection<AuctionStatus> statuses);

    /**
     * 입찰을 위해 경매 한 건을 잠그고 읽는다
     * <p>
     * 잠금 없이 처리하면 두 입찰이 같은 현재가를 읽고 둘 다 통과한 뒤 둘 다 쓴다.
     * 같은 금액의 최고가가 두 건 남거나 나중 것이 낮은 금액으로 덮어써서, 낙찰자를 말할 수 없게 된다.
     * <p>
     * join fetch 를 붙이지 않는다. FOR UPDATE 에 조인이 걸리면 경매글과 차량 행까지 잠겨서
     * 잠금 범위를 경매 한 건으로 제한한 의미가 없어진다.
     * <p>
     * 대기 시간은 MySQL 의 innodb_lock_wait_timeout 을 따른다.
     * MySQL 은 FOR UPDATE 에 숫자 대기 시간을 받지 않아 JPA 잠금 타임아웃 힌트로는 조절할 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :auctionId")
    Optional<Auction> findByIdForUpdate(@Param("auctionId") long auctionId);

    /**
     * 이 경매에 걸린 차량의 판매자인지, 판매자는 자기 차량에 입찰할 수 없다
     * 판매자 id 를 꺼내 비교하지 않고 존재 여부만 묻는 건 existsActiveByVehicleId 와 같은 방식이다
     */
    @Query("""
            select count(a) > 0
            from Auction a
            join a.post p
            join p.vehicle v
            where a.id = :auctionId
            and v.seller.id = :userId
            """
    )
    boolean isSeller(@Param("auctionId") long auctionId, @Param("userId") long userId);

    /**
     * 시작 시각이 지났는데 아직 예약 상태인 경매의 id
     */
    @Query("""
            select a.id
            from Auction a
            where a.status = :scheduled
                and a.startTime <= :now
            order by a.startTime
            """)
    List<Long> findStartableIds(@Param("scheduled") AuctionStatus scheduled,
                                @Param("now") LocalDateTime now,
                                Limit limit);

    /**
     * 마감이 지났는데 아직 진행 중인 경매의 id
     */
    @Query("""
             select a.id
             from Auction a
             where a.status = :inProgress
                 and a.currentEndTime <= :now
             order by a.currentEndTime
            """)
    List<Long> findClosableIds(@Param("inProgress") AuctionStatus inProgress,
                               @Param("now") LocalDateTime now,
                               Limit limit);

    @Query("""
             select a 
             from Auction a join fetch a.post
             where a.id = :auctionId
            """)
    Optional<Auction> findWithPostById(@Param("auctionId") long auctionId);
}
