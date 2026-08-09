package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 경매방 조회와 브로드캐스트가 한 번에 읽어오는 경매·차량·낙찰자
 */
public record AuctionRoomDetail(
        long auctionId,
        AuctionStatus status,
        long startPrice,
        long currentPrice,
        LocalDateTime roomOpenAt,
        LocalDateTime startTime,
        LocalDateTime currentEndTime,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        int mileage,
        FuelType fuelType,
        String thumbnailUrl,
        Long winnerId,
        MaskedName winner
) {

    // JPQL 이 넘기는 원시 값을 받아 표시가 해소와 마스킹을 한 번에 한다
    // 위 정규 생성자는 실명을 받을 수 없으므로 조회 경로에는 이 생성자만 걸린다
    public AuctionRoomDetail(long auctionId,
                             AuctionStatus status,
                             long startPrice,
                             Long currentPrice,
                             LocalDateTime roomOpenAt,
                             LocalDateTime startTime,
                             LocalDateTime currentEndTime,
                             Manufacturer manufacturer,
                             String model,
                             int modelYear,
                             int mileage,
                             FuelType fuelType,
                             String thumbnailUrl,
                             Long winnerId,
                             String winnerRealName) {

        this(auctionId,
                status,
                startPrice,
                currentPrice != null ? currentPrice : startPrice,
                roomOpenAt,
                startTime,
                currentEndTime,
                manufacturer,
                model,
                modelYear,
                mileage,
                fuelType,
                thumbnailUrl,
                winnerId,
                winnerRealName != null ? MaskedName.mask(winnerRealName) : null);
    }

    /**
     * 주어진 시각 기준의 방 단계
     */
    public RoomPhase phaseAt(LocalDateTime now) {
        return RoomPhase.at(now, roomOpenAt, startTime, currentEndTime);
    }

    /**
     * 화면에 보일 차량 요약
     */
    public VehicleSummary vehicle() {
        return new VehicleSummary(manufacturer, model, modelYear, mileage, fuelType, thumbnailUrl);
    }

    /**
     * 낙찰자 이름, 낙찰 확정 전에는 없다
     */
    public Optional<MaskedName> winnerName() {
        return Optional.ofNullable(winner);
    }

    /**
     * 조회한 사람이 낙찰자인지
     */
    public boolean isWonBy(long viewerId) {
        return Optional.ofNullable(winnerId).filter(id -> id == viewerId).isPresent();
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
    // ENDED 는 최고 입찰자가 있을 때만 확정되므로 그때의 현재가는 시작가로 해소된 값이 아니라 실제 입찰가다
    public Optional<Long> winningPrice() {
        return status == AuctionStatus.ENDED ? Optional.of(currentPrice) : Optional.empty();
    }
}