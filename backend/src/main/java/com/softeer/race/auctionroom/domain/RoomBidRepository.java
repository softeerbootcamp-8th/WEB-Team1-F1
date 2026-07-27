package com.softeer.race.auctionroom.domain;

import com.softeer.race.bid.domain.Bid;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 경매방 화면에 필요한 입찰 집계와 최근 호가 조회
 */
public interface RoomBidRepository extends Repository<Bid, Long> {

    @Query("select count(distinct b.bidder.id) from Bid b where b.auction.id = :auctionId")
    int countBidders(@Param("auctionId") long auctionId);

    /**
     * 최신순 호가, 이름은 담기는 시점에 마스킹된다
     */
    @Query("""
            select new com.softeer.race.auctionroom.domain.RecentBid(u.nickname, b.amount, b.createdAt)
            from Bid b
            join b.bidder u
            where b.auction.id = :auctionId
            order by b.id desc
            """)
    List<RecentBid> findRecentBids(@Param("auctionId") long auctionId, Limit limit);
}