package com.softeer.race.progress.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 판매자의 차 한 대에 대해 평가 · 경매글 · 경매를 한 줄로 모은 것
 * <p>
 * 뒤쪽 단계일수록 앞쪽 값이 비어 있을 수 있고 그 반대도 마찬가지다. 판매 신청으로 들어온 차량은
 * 평가 값이 전부 null이고, 방문견적으로 들어온 차량은 경매 값이 전부 null이다. 그래서 경매
 * 시작가처럼 원본이 원시 타입인 값도 여기서는 래퍼로 받는다.
 * <p>
 * 단계는 담지 않는다. 저장된 사실이 아니라 이 행에서 파생되는 값이라 {@link #stage()}로 계산한다.
 */
public record SellerProgressRow(
        Long vehicleId,
        Manufacturer manufacturer,
        String model,
        Integer modelYear,
        Integer mileage,
        String plateNumber,
        Long estimatedPrice,
        LocalDateTime appliedAt,

        EvaluationStatus evaluationStatus,
        Long evaluatorId,
        LocalDate visitDate,
        String rejectReason,

        String thumbnailUrl,
        LocalDateTime listingRemovedAt,

        Long auctionId,
        AuctionStatus auctionStatus,
        Long startPrice,
        Long currentPrice,
        LocalDateTime startTime,
        LocalDateTime endTime
) {

    public ProgressStage stage() {
        return ProgressStage.of(evaluationStatus, evaluatorId != null, auctionStatus, listingRemovedAt != null);
    }
}
