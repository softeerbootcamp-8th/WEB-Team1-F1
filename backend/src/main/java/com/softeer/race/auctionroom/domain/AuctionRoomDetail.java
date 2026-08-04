package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 경매방 조회가 한 번에 읽어오는 경매·차량·낙찰자
 */
public record AuctionRoomDetail(
        long auctionId,
        AuctionStatus status,
        long startPrice,
        Long currentPrice,
        LocalDateTime roomOpenAt,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        String thumbnailUrl,
        Long winnerId,
        String winnerRealName
) {
    /**
     * 단계 판정과 가격 표시를 맡는 부분
     */
    public AuctionRoomSnapshot snapshot() {
        return new AuctionRoomSnapshot(startPrice, currentPrice, roomOpenAt, startTime, endTime);
    }

    /**
     * 화면에 보일 차량 요약
     */
    public VehicleSummary vehicle() {
        return new VehicleSummary(manufacturer, model, modelYear, mileage, fuelType);
    }

    /**
     * 낙찰자 이름, 낙찰 확정 전에는 없다
     */
    public Optional<MaskedName> winnerName() {
        return Optional.ofNullable(winnerRealName).map(MaskedName::new);
    }

    /**
     * 조회한 사람이 낙찰자인지
     */
    public boolean isWonBy(long viewerId) {
        return winnerId != null && winnerId == viewerId;
    }

    /**
     * 확정된 경매 결과, 아직 끝나지 않았으면 없다
     */
    // 시각이 아니라 상태를 본다, 마감과 확정 사이에는 낙찰인지 유찰인지 알 수 없다
    public Optional<AuctionOutcome> outcome() {
        return switch (status) {
            case ENDED -> Optional.of(AuctionOutcome.SOLD);
            case FAILED -> Optional.of(AuctionOutcome.UNSOLD);
            case SCHEDULED, IN_PROGRESS -> Optional.empty();
        };
    }

    /**
     * 최종 낙찰가, 유찰이거나 확정 전이면 없다
     */
    // 진행 중의 현재가는 낙찰가가 아니다, 연장이 걸리면 더 오를 수 있다
    public Optional<Long> winningPrice() {
        return status == AuctionStatus.ENDED ? Optional.ofNullable(currentPrice) : Optional.empty();
    }
}