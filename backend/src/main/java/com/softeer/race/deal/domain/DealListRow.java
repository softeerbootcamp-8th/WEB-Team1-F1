package com.softeer.race.deal.domain;

import com.softeer.race.common.domain.MaskedName;

import java.time.LocalDateTime;

/**
 * 거래 목록 카드 한 건
 */
public record DealListRow(
        Long dealId,
        DealStatus status,
        Long finalPrice,
        LocalDateTime statusChangedAt,
        Long auctionId,
        String model,
        String thumbnailUrl,
        Long sellerId,
        MaskedName counterpartName
) {

    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    // 상대 실명은 필드로 남지 않아, 마스킹을 빠뜨리는 실수가 타입으로 막힌다
    public DealListRow(Long dealId, DealStatus status, Long finalPrice, LocalDateTime statusChangedAt,
                       Long auctionId, String model, String thumbnailUrl,
                       Long sellerId, String counterpartRealName) {
        this(dealId, status, finalPrice, statusChangedAt, auctionId, model, thumbnailUrl,
                sellerId, MaskedName.mask(counterpartRealName));
    }

    /**
     * 조회한 사람이 이 거래에서 선 쪽, 화면이 액션 버튼을 가르는 기준이다
     */
    public DealSide sideOf(long viewerId) {
        return sellerId == viewerId ? DealSide.SELLER : DealSide.BUYER;
    }
}