package com.softeer.race.auction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

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
}
