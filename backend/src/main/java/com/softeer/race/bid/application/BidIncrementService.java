package com.softeer.race.bid.application;

import com.softeer.race.bid.domain.BidIncrementBandRepository;
import com.softeer.race.bid.domain.BidIncrementTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 가격 구간표를 조회하는 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidIncrementService {

    private final BidIncrementBandRepository bidIncrementBandRepository;

    // 값 변경이 재기동 없이 반영되도록 매번 조회한다, 5행 조회 비용은 입찰 트랜잭션에서 무시할 수준이다
    public BidIncrementTable loadTable() {
        return new BidIncrementTable(bidIncrementBandRepository.findAll());
    }
}
