package com.softeer.race.bid.domain;

import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.VehicleName;
import java.time.LocalDateTime;

/**
 * 잠금을 잡기 전에 끝낼 수 있는 판정에 필요한 값들.
 */
public record BidPreCheck(
        String bidderRealName,
        Long sellerId,
        VehicleName vehicleName,
        Long startPrice,
        Long currentPrice,
        LocalDateTime startTime,
        LocalDateTime currentEndTime) {

    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다, 원본 모델명은 필드로 남지 않는다.
    // 경매가 없으면 좌측 조인이라 제조사부터 null 로 오므로 그때는 이름을 만들지 않는다
    public BidPreCheck(String bidderRealName, Long sellerId,
                       Manufacturer manufacturer, String model,
                       Long startPrice, Long currentPrice,
                       LocalDateTime startTime, LocalDateTime currentEndTime) {
        this(bidderRealName, sellerId,
                manufacturer == null ? null : new VehicleName(manufacturer, model),
                startPrice, currentPrice, startTime, currentEndTime);
    }

    public boolean hasAuction() {
        return startPrice != null;
    }

    /** 잠금 앞 판정에 쓸 사본. 경매 값들을 언박싱하므로 hasAuction 확인 뒤에 불러야 한다. */
    public AuctionBidSnapshot toSnapshot() {
        return new AuctionBidSnapshot(sellerId, startPrice, currentPrice, startTime, currentEndTime);
    }
}
