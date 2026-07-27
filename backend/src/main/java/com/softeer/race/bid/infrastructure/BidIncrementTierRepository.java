package com.softeer.race.bid.infrastructure;

import com.softeer.race.bid.domain.BidIncrementTier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidIncrementTierRepository extends JpaRepository<BidIncrementTier, Long> {
}
