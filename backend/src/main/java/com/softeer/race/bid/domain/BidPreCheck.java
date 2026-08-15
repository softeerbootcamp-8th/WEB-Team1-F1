package com.softeer.race.bid.domain;

import com.softeer.race.user.domain.Role;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.VehicleName;
import java.time.LocalDateTime;

/**
 * 잠금을 잡기 전에 끝낼 수 있는 판정에 필요한 값들.
 */
public record BidPreCheck(
        Role role,
        String bidderRealName,
        Long sellerId,
        VehicleName vehicleName,
        Long startPrice,
        Long currentPrice,
        LocalDateTime startTime,
        LocalDateTime currentEndTime) {

    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다, 원본 모델명은 필드로 남지 않는다.
    // 경매가 없으면 좌측 조인이라 제조사부터 null 로 오므로 그때는 이름을 만들지 않는다
    public BidPreCheck(Role role, String bidderRealName, Long sellerId,
                       Manufacturer manufacturer, String model,
                       Long startPrice, Long currentPrice,
                       LocalDateTime startTime, LocalDateTime currentEndTime) {
        this(role, bidderRealName, sellerId,
                manufacturer == null ? null : new VehicleName(manufacturer, model),
                startPrice, currentPrice, startTime, currentEndTime);
    }

    public boolean hasAuction() {
        return startPrice != null;
    }

    public boolean isEvaluator() {
        return role == Role.EVALUATOR;
    }

    public boolean isSeller(long bidderId) {
        return sellerId == bidderId;
    }

    /**
     * 확실히 떨어질 금액이면 잠금 앞에서 거절한다.
     * 가격이 단조 증가하므로 낡은 현재가로 "너무 낮다"면 최신 값으로도 반드시 너무 낮다 — 오거절이 불가능하다.
     */
    public void rejectIfBelowMinimum(BidIncrementTable table, long amount, LocalDateTime now) {
        if (now.isBefore(startTime) || !now.isBefore(currentEndTime)) {
            return;
        }
        table.ruleFor(startPrice, currentPrice).validateMinimum(amount);
    }
}
