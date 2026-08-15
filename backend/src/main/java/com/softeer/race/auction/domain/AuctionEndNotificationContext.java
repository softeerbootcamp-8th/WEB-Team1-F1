package com.softeer.race.auction.domain;

import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.VehicleName;

/** 경매 종료 알림을 만들 때 필요한 값만 읽은 스냅샷 */
public record AuctionEndNotificationContext(
        long sellerId,
        VehicleName vehicleName,
        Long finalPrice
) {
    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    public AuctionEndNotificationContext(
            long sellerId, Manufacturer manufacturer, String model, Long finalPrice) {
        this(sellerId, new VehicleName(manufacturer, model), finalPrice);
    }

    public long finalPriceOrThrow(long auctionId) {
        if (finalPrice == null) {
            throw new IllegalStateException(
                    "낙찰된 경매에 최종가가 없다, 경매 %d".formatted(auctionId));
        }

        return finalPrice;
    }
}
