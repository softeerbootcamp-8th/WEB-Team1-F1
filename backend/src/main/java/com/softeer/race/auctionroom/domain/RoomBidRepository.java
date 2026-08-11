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

    /**
     * 입찰 건수와 입찰자 수, 한 사람이 여러 번 넣으면 건수만 늘어난다
     */
    @Query("""
            select new com.softeer.race.auctionroom.domain.BidStats(count(b), count(distinct b.bidder.id))
            from Bid b
            where b.auction.id = :auctionId
            """)
    BidStats findStats(@Param("auctionId") long auctionId);

    /**
     * 그 사람이 이 경매에 넣은 가장 높은 입찰가, 한 번도 넣지 않았으면 없다
     */
    @Query("""
            select max(b.amount)
            from Bid b
            where b.auction.id = :auctionId
              and b.bidder.id = :bidderId
            """)
    Long findTopAmount(@Param("auctionId") long auctionId, @Param("bidderId") long bidderId);

    /**
     * 주어진 금액보다 높이 부른 사람 수, 순위를 매기는 데 쓴다
     */
    @Query("""
            select count(distinct b.bidder.id)
            from Bid b
            where b.auction.id = :auctionId
              and b.amount > :amount
            """)
    long countBiddersAbove(@Param("auctionId") long auctionId, @Param("amount") long amount);

    /**
     * 가격이 오른 과정, 선을 왼쪽부터 그리도록 시간순이고 건수 제한이 없다
     */
    // 호가창과 달리 이름을 읽지 않는다, 스무 건 제한도 없다
    @Query("""
            select new com.softeer.race.auctionroom.domain.PricePoint(
                b.bidder.id, b.amount, b.createdAt)
            from Bid b
            where b.auction.id = :auctionId
            order by b.id
            """)
    List<PricePoint> findPriceCurve(@Param("auctionId") long auctionId);

    /**
     * 최신순 호가, 이름은 담기는 시점에 마스킹된다
     */
    @Query("""
            select new com.softeer.race.auctionroom.domain.RecentBid(
                u.id, u.realName, u.role, b.amount, b.createdAt)
            from Bid b
            join b.bidder u
            where b.auction.id = :auctionId
            order by b.id desc
            """)
    List<RecentBid> findRecentBids(@Param("auctionId") long auctionId, Limit limit);
}