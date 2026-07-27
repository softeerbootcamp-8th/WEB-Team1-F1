package com.softeer.race.auctionroom.domain;

import com.softeer.race.bid.domain.Bid;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 경매방 화면에 필요한 입찰 집계와 최근 호가 조회
 */
public interface RoomBidRepository extends Repository<Bid, Long> {

    int RECENT_BID_LIMIT = 20;

    @EntityGraph(attributePaths = "bidder")
    List<Bid> findByAuctionIdOrderByIdDesc(long auctionId, Limit limit);

    @Query("select count(distinct b.bidder.id) from Bid b where b.auction.id = :auctionId")
    int countBidders(@Param("auctionId") long auctionId);

    default List<Bid> findRecentBids(long auctionId) {
        return findByAuctionIdOrderByIdDesc(auctionId, Limit.of(RECENT_BID_LIMIT));
    }
}