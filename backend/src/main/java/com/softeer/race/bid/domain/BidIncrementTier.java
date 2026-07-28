package com.softeer.race.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입찰 가격 구간별 상승가 정보를 관리하는 엔티티
 * 데이터베이스의 테이블과 매핑되며, 한 행이 하나의 가격 구간 정책을 나타낸다.
 */
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
    BidIncrementTier(long minPrice, long increment) {
        this.minPrice = minPrice;
        this.increment = increment;
    }

    /** 하한이 이 금액 이하인가, 실제 구간 선택은 이런 구간 중 하한이 가장 큰 것을 고른다 */
    boolean startsAtOrBelow(long price) {
        return minPrice <= price;
    }

    /** 상승가 격자 위의 값 중 현재가보다 큰 최솟값 */
    long nextBidPrice(long currentPrice) {
        return (currentPrice / increment + 1) * increment;
    }
}