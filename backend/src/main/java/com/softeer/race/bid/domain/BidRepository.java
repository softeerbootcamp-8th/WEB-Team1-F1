package com.softeer.race.bid.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 이 경매의 가장 최근 입찰, 연속 입찰 금지 판정에 쓴다
     * 최고가는 Auction.currentPrice 가 들고 있지만 누가 냈는지는 여기만 안다
     */
    Optional<Bid> findFirstByAuctionIdOrderByIdDesc(long auctionId);

    /**
     * 이 경매에 입찰한 사람 중 낙찰자를 뺀 회원 id
     * <p>
     * 한 사람이 같은 경매에 여러 번 입찰하는 것이 정상이라 distinct 로 접는다. 접지 않으면 종료
     * 알림이 입찰 횟수만큼 쌓여 알림함이 같은 문구로 찬다. 낙찰자는 낙찰 알림을 따로 받으므로 뺀다.
     * <p>
     * 입찰 엔티티가 아니라 id 만 뽑는다. 엔티티로 받으면 bidder 가 지연 로딩이라 사람 수만큼 조회가
     * 따라붙는데, 발행이 트랜잭션 안이라 예외도 없이 조용히 늘어난다.
     */
    @Query("""
            select distinct b.bidder.id
            from Bid b
            where b.auction.id = :auctionId
                and b.bidder.id <> :winnerId
            """)
    List<Long> findOtherBidderIds(@Param("auctionId") long auctionId, @Param("winnerId") long winnerId);
}
