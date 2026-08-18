package com.softeer.race.bid.domain;

import com.softeer.race.user.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BidPreCheckRepository extends Repository<User, Long> {

    @Query("""
            select new com.softeer.race.bid.domain.BidPreCheck(
                u.realName, v.seller.id, v.manufacturer, v.model,
                a.startPrice, a.currentPrice, a.startTime, a.currentEndTime)
            from User u
            left join Auction a on a.id = :auctionId
            left join a.post p
            left join p.vehicle v
            where u.id = :bidderId
            """)
    Optional<BidPreCheck> find(
            @Param("auctionId") long auctionId,
            @Param("bidderId") long bidderId);
}
