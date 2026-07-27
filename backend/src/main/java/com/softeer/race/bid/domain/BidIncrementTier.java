package com.softeer.race.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가격 구간별 최저 입찰 상승가, 한 행이 한 구간이다 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BidIncrementTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 구간 하한, 이 값을 포함한다. 상한은 다음 구간의 하한이고 마지막 구간은 상한이 없다 */
    @Column(nullable = false, unique = true)
    private long minPrice;

    /** 이 구간의 최저 상승가, 다음 입찰가는 이 값의 배수여야 한다 */
    @Column(nullable = false)
    private long increment;

    /** 행은 data.sql이 넣으므로 프로덕션에서는 생성하지 않는다, 테스트에서 구간을 조립하기 위한 생성자다 */
    public BidIncrementTier(long minPrice, long increment) {
        this.minPrice = minPrice;
        this.increment = increment;
    }
}