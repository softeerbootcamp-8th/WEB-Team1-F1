package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 경매방 화면에 필요한 경매 조회
 */
public interface AuctionRoomRepository extends Repository<Auction, Long> {

    /**
     * 방 화면에 필요한 값만 뽑는 조회, 삭제된 경매글의 방은 없는 것으로 본다
     */
    @Query("""
            select new com.softeer.race.auctionroom.domain.AuctionRoomDetail(
                a.id, a.status, a.startPrice, a.currentPrice, a.roomOpenAt, a.startTime, a.currentEndTime,
                v.manufacturer, v.model, v.modelYear, v.mileage, v.fuelType,
                v.mainPhotoUrl, v.diagnosticReportUrl, w.id, w.realName)
            from Auction a
            join a.post p
            join p.vehicle v
            left join a.winner w
            where a.id = :auctionId
              and p.deletedAt is null
            """)
    Optional<AuctionRoomDetail> findDetailById(@Param("auctionId") long auctionId);
}