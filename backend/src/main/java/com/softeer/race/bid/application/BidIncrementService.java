package com.softeer.race.bid.application;

import com.softeer.race.bid.domain.BidIncrementPolicy;
import com.softeer.race.bid.infrastructure.BidIncrementTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구간표를 읽어 정책 객체로 조립한다, 산출 규칙 자체는 BidIncrementPolicy가 갖는다
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
