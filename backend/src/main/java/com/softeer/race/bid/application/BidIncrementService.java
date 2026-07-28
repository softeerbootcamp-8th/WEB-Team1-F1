package com.softeer.race.bid.application;

import com.softeer.race.bid.domain.BidIncrementPolicy;
import com.softeer.race.bid.infrastructure.BidIncrementTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 가격 구간 정책을 조회하고 다음 최소 입찰가를 계산하는 서비스
 */
@Service
@Transactional(readOnly = true)
public class BidIncrementService {

    private final BidIncrementTierRepository bidIncrementTierRepository;

    public BidIncrementService(BidIncrementTierRepository bidIncrementTierRepository) {
        this.bidIncrementTierRepository = bidIncrementTierRepository;
    }

    // 값 변경이 재기동 없이 반영되도록 매번 조회한다, 5행 조회 비용은 입찰 트랜잭션에서 무시할 수준이다
    public BidIncrementPolicy policy() {
        return new BidIncrementPolicy(bidIncrementTierRepository.findAll());
    }

    public long nextBidPrice(long currentPrice) {
        return policy().nextBidPrice(currentPrice);
    }
}
