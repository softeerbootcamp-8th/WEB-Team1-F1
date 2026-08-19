package com.softeer.race.deal.domain;

import com.softeer.race.common.domain.MaskedName;

import java.time.LocalDateTime;

/**
 * 거래 상세 한 건
 * <p>
 * 목록 카드와 나눈다. 상세에만 필요한 값이 있고, 목록은 여러 건을 한 번에 읽어서
 * 안 쓰는 컬럼을 그만큼 곱해 읽게 된다.
 */
public record DealDetailRow(
        Long dealId,
        DealStatus status,
        Long finalPrice,
        LocalDateTime statusChangedAt,
        LocalDateTime openedAt,
        CancellationReason cancellationReason,
        Long auctionId,
        String model,
        Integer modelYear,
        Integer mileage,
        String thumbnailUrl,
        String documentUrl,
        LocalDateTime transportAt,
        String transportLocation,
        LocalDateTime deliveryAt,
        String deliveryLocation,
        Long sellerId,
        MaskedName counterpartName
) {

    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    // 상대 실명은 필드로 남지 않아, 마스킹을 빠뜨리는 실수가 타입으로 막힌다
    public DealDetailRow(Long dealId, DealStatus status, Long finalPrice,
                         LocalDateTime statusChangedAt, LocalDateTime openedAt,
                         CancellationReason cancellationReason,
                         Long auctionId, String model, Integer modelYear, Integer mileage,
                         String thumbnailUrl,
                         String documentUrl, LocalDateTime transportAt, String transportLocation,
                         LocalDateTime deliveryAt, String deliveryLocation,
                         Long sellerId, String counterpartRealName) {
        this(dealId, status, finalPrice, statusChangedAt, openedAt, cancellationReason,
                auctionId, model, modelYear, mileage, thumbnailUrl,
                documentUrl, transportAt, transportLocation, deliveryAt, deliveryLocation,
                sellerId, MaskedName.mask(counterpartRealName));
    }

    public DealSide sideOf(long viewerId) {
        return sellerId == viewerId ? DealSide.SELLER : DealSide.BUYER;
    }

    /**
     * 이 취소의 귀책, 취소되지 않았으면 비어 있다
     */
    // 화면이 사유에서 귀책을 다시 계산하지 않게 서버가 풀어 준다, 사유→귀책 표는 서버 정책이다
    public FaultParty faultParty() {
        return cancellationReason != null ? cancellationReason.faultParty() : null;
    }

    /**
     * 조회한 사람이 지금 움직일 차례인지, 화면이 액션 버튼을 켜는 기준이다
     * <p>
     * 상태에서 곧바로 뽑는다. 화면이 단계 표를 따로 들고 판정하면 서버와 어긋나는 순간
     * 버튼은 있는데 눌리지 않는 상태가 된다
     */
    public boolean actionRequiredFor(long viewerId) {
        return status.waitingFor() == sideOf(viewerId);
    }
}
