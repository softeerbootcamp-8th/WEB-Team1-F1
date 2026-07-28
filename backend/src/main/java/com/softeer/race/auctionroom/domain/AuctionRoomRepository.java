package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * 경매방 화면에 필요한 경매 조회
 */
public interface AuctionRoomRepository extends Repository<Auction, Long> {

    Optional<Auction> findById(Long auctionId);
}