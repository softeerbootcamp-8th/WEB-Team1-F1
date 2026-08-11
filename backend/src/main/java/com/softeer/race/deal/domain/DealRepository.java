package com.softeer.race.deal.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    /** 지연 연관 세 단계를 열지 않고 거래 차량의 모델만 읽는다. */
    @Query("""
            select v.model
            from Deal d
            join d.auction a
            join a.post p
            join p.vehicle v
            where d.id = :dealId
            """)
    Optional<String> findVehicleModelById(@Param("dealId") long dealId);
}
