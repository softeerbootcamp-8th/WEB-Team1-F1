package com.softeer.race.bid.domain;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<엔티티, ID타입>을 상속하면 런타임에 이 인터페이스의 구현체를 만들어서 빈으로 등록.
// findAll(), save(), findById()함수도 가짐
public interface BidIncrementBandRepository extends JpaRepository<BidIncrementBand, Long> {
}
