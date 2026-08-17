package com.softeer.race.bid.application;

import com.softeer.race.bid.domain.BidIncrementBandRepository;
import com.softeer.race.bid.domain.BidIncrementTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 입찰 가격 구간표를 조회하는 서비스
 */
// 클래스 레벨 @Transactional 을 붙이지 않는다 - 트랜잭션 밖의 AuctionBidGate 가 loadTable 을 부르는데
// 붙이면 캐시 히트에도 트랜잭션이 열린다.
@Service
@RequiredArgsConstructor
public class BidIncrementService {

    private final BidIncrementBandRepository bidIncrementBandRepository;

    /**
     * 한 번 읽어 붙잡아 두는 구간표. 운영 중 값이 바뀌지 않는 정책 데이터라 입찰마다 다시 읽을 이유가 없다.
     */
    private volatile BidIncrementTable cached;

    /**
     * 구간표를 돌려준다. 처음 한 번만 DB 를 읽고 이후로는 메모리에서 나간다.
     */
    public BidIncrementTable loadTable() {
        BidIncrementTable table = cached;
        return (table != null) ? table : reload();
    }

    /**
     * DB 를 다시 읽어 캐시를 갈아 끼운다. 구간표 행을 바꿨다면 재기동하거나 이 메서드를 불러야 반영된다.
     */
    public BidIncrementTable reload() {
        BidIncrementTable table = new BidIncrementTable(bidIncrementBandRepository.findAll());
        cached = table;
        return table;
    }
}
