package com.softeer.race.bid.domain;

import com.softeer.race.bid.exception.BidErrorCode;
import com.softeer.race.common.exception.BusinessException;
import java.util.Comparator;
import java.util.List;

/** 가격 구간표 전체, 구간 선택과 다음 최소 입찰가 산출을 담당한다 */
public class BidIncrementPolicy {

    /** 하한 오름차순, findAll의 반환 순서를 신뢰할 수 없으므로 받는 즉시 정렬한다 */
    private final List<BidIncrementTier> tiers;

    public BidIncrementPolicy(List<BidIncrementTier> tiers) {
        this.tiers = tiers.stream()
                .sorted(Comparator.comparingLong(BidIncrementTier::getMinPrice))
                .toList();
    }

    /** 현재가 기준 다음 최소 입찰가, 해당 구간 상승가의 배수 중 현재가보다 큰 최솟값이다 */
    public long nextBidPrice(long currentPrice) {
        return tierOf(currentPrice).nextBidPrice(currentPrice);
    }

    public List<BidIncrementTier> tiers() {
        return tiers;
    }

    // 하한이 현재가 이하인 구간 중 하한이 가장 큰 것, 역순으로 보면 처음 걸리는 것이다
    // 구간을 찾지 못하면 기준이 깨진 것이므로 기본값으로 대체하지 않고 중단한다
    private BidIncrementTier tierOf(long price) {
        return tiers.reversed().stream()
                .filter(tier -> tier.startsAtOrBelow(price))
                .findFirst()
                .orElseThrow(() -> new BusinessException(BidErrorCode.BID_INCREMENT_TIER_NOT_FOUND));
    }
}
