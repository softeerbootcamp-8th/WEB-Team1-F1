package com.softeer.race.bid.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 이 경매의 가장 최근 입찰, 연속 입찰 금지 판정에 쓴다
     * 최고가는 Auction.currentPrice 가 들고 있지만 누가 냈는지는 여기만 안다
     */
    Optional<Bid> findFirstByAuctionIdOrderByIdDesc(long auctionId);
}
