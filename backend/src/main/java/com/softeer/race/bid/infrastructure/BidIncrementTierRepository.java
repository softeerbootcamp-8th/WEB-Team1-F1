package com.softeer.race.bid.infrastructure;

import com.softeer.race.bid.domain.BidIncrementTier;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<엔티티, ID타입>을 상속하면 런타임에 이 인터페이스의 구현체를 만들어서 빈으로 등록.
// findAll(), save(), findById()함수도 가짐
public interface BidIncrementTierRepository extends JpaRepository<BidIncrementTier, Long> {
}
