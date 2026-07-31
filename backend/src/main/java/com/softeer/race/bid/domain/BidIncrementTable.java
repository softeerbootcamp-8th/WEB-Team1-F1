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
     * 지금 낼 수 있는 금액의 기준
     * 첫 입찰은 시작가를 그대로 낼 수 있고, 이후는 현재가에서 최소 한 칸 올려야 한다
     * <p>
     * 첫 입찰인지는 현재가가 없는 것으로 알 수 있지만, 그때 기준이 될 금액은 표가 모른다.
     * 구간표는 가격대별 상승가만 알고 시작가는 경매마다 다르다. 그래서 함께 받는다.
     *
     * @param startPrice   첫 입찰일 때 기준이 되는 금액, 구간을 고르는 입력이기도 하다
     * @param currentPrice 아직 입찰이 없으면 null
     */
    public BidRule ruleFor(long startPrice, Long currentPrice) {
        boolean firstBid = (currentPrice == null);
        long base = firstBid ? startPrice : currentPrice;
        long increment = bandOf(base).getIncrement();

        return new BidRule(base, increment, firstBid ? base : base + increment);
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
