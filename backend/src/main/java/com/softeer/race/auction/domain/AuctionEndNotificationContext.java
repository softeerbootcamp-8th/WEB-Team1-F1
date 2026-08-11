package com.softeer.race.auction.domain;

/** 경매 종료 알림을 만들 때 필요한 값만 읽은 스냅샷 */
public record AuctionEndNotificationContext(
        long sellerId,
        String vehicleModel,
        Long finalPrice
) {
    public long finalPriceOrThrow(long auctionId) {
        if (finalPrice == null) {
            throw new IllegalStateException(
                    "낙찰된 경매에 최종가가 없다, 경매 %d".formatted(auctionId));
        }

        return finalPrice;
    }
}
