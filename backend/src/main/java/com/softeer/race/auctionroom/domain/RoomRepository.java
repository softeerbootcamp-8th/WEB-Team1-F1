package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 경매방 화면에 필요한 경매 조회
 */
public interface RoomRepository extends Repository<Auction, Long> {

    /**
     * 방 화면에 필요한 값만 뽑는 조회, 삭제된 경매글의 방은 없는 것으로 본다
     */
    // 번호판을 뽑지 않는다, 경매에 필요하지 않고 차량을 특정할 수 있다
    @Query("""
            select new com.softeer.race.auctionroom.domain.RoomDetail(
                a.id, a.status, a.startPrice, a.currentPrice, a.roomOpenAt, a.startTime, a.currentEndTime,
                a.extensionCount,
                v.id, v.manufacturer, v.model, v.modelYear, v.mileage, v.fuelType,
                v.diagnosticReportUrl, v.seller.id, w.id, w.realName)
            from Auction a
            join a.post p
            join p.vehicle v
            left join a.winner w
            where a.id = :auctionId
              and p.deletedAt is null
            """)
    Optional<RoomDetail> findDetailById(@Param("auctionId") long auctionId);

    /**
     * 화면에 보일 차량 사진 주소, 등록 순서 그대로다
     */
    @Query("""
            select i.imageUrl
            from Auction a
            join a.post p
            join p.vehicle v
            join VehicleImage i on i.vehicle = v
            where a.id = :auctionId
            order by i.sortOrder
            """)
    List<String> findPhotoUrls(@Param("auctionId") long auctionId);
}