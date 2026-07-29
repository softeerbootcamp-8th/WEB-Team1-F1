package com.softeer.race.bid.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 입찰 가격 구간표를 관리하는 도메인 클래스
 */
public class BidIncrementTable {

    /**
     * 입찰 가격 구간 리스트를 하한가 기준 오름차순으로 정렬하여 초기화합니다.
     */
    private final List<BidIncrementBand> bands;

    public BidIncrementTable(List<BidIncrementBand> bands) {
        this.bands = bands.stream()
                .sorted(Comparator.comparingLong(BidIncrementBand::getMinPrice))
                .toList();
    }

    /**
     * 현재가 기준 다음 최소 입찰가를 리턴, 현재가에 그 구간의 상승가를 더한 값이다
     */
    public long nextBidPrice(long currentPrice) {
        return bandOf(currentPrice).nextBidPrice(currentPrice);
    }

    /**
     * 전체 입찰 가격 구간 리스트를 반환합니다.
     */
    public List<BidIncrementBand> getBands() {
        return bands;
    }

    /**
     * 주어진 가격에 해당하는 입찰 가격 구간을 조회합니다.
     * 역순으로 정렬 후 위에서부터 차례로 해당 구간의 하한가보다 큰지 조회한다.
     */
    private BidIncrementBand bandOf(long price) {
        return bands.reversed().stream()
                .filter(band -> band.startsAtOrBelow(price))
                .findFirst()
                // 못 찾으면 기본값으로 대체하지 않고 중단한다, 근거 없는 상승가로 입찰이 성립해서는 안 된다
                // 사용자가 고칠 수 없는 서버 데이터 파손이라 BusinessException을 쓰지 않는다
                // 최후 방어선으로 넘겨 스택 트레이스를 남긴다, 아래 메시지는 로그에만 남고 응답에 노출되지 않는다
                .orElseThrow(() -> new IllegalStateException(
                        "현재가 %d원을 담당하는 구간이 없다, 구간표 %d행".formatted(price, bands.size())));
    }
}
