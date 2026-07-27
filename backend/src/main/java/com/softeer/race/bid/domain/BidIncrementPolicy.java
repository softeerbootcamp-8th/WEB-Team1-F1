package com.softeer.race.bid.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 가격 구간표 전체를 관리하며, 구간 선택과 다음 최소 입찰가 산출 책임을 담당하는 도메인 객체.
 */
public class BidIncrementPolicy {

    /**
     * 하한 오름차순, findAll의 반환 순서를 신뢰할 수 없으므로 받는 즉시 정렬한다
     */
    private final List<BidIncrementTier> tiers;

    public BidIncrementPolicy(List<BidIncrementTier> tiers) {
        this.tiers = tiers.stream()
                .sorted(Comparator.comparingLong(BidIncrementTier::getMinPrice))
                .toList();
    }

    /**
     * 현재가 기준 다음 최소 입찰가, 해당 구간 상승가의 배수 중 현재가보다 큰 최솟값이다
     */
    public long nextBidPrice(long currentPrice) {
        return tierOf(currentPrice).nextBidPrice(currentPrice);
    }

    public List<BidIncrementTier> tiers() {
        return tiers;
    }

    // 하한이 현재가 이하인 구간 중 하한이 가장 큰 것, 역순으로 보면 처음 걸리는 것이다
    // 못 찾으면 기본값으로 대체하지 않고 중단한다, 근거 없는 상승가로 입찰이 성립해서는 안 된다
    // 사용자가 고칠 수 없는 서버 데이터 파손이라 BusinessException을 쓰지 않는다
    // 최후 방어선으로 흘려보내 스택 트레이스를 남기고, 아래 메시지는 로그에만 남아 응답에 노출되지 않는다
    private BidIncrementTier tierOf(long price) {
        return tiers.reversed().stream()
                .filter(tier -> tier.startsAtOrBelow(price))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "현재가 %d원을 담당하는 구간이 없다, 구간표 %d행".formatted(price, tiers.size())));
    }
}
