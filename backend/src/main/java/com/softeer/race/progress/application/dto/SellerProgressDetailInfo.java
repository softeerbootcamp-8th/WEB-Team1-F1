package com.softeer.race.progress.application.dto;

import com.softeer.race.progress.domain.ProgressStage;
import com.softeer.race.progress.domain.SellerProgressRow;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 진행 상황 한 건의 전부
 * <p>
 * 단계에 따라 비는 값이 많다. 방문견적으로 들어와 아직 진단 전이면 주행거리 · 예상 시세 · 경매
 * 관련 값이 모두 비고, 판매 신청으로 들어왔다면 평가 관련 값이 전부 빈다. 단계별로 응답 모양을
 * 나누지 않는 것은 화면이 단계마다 다른 스키마를 분기해 읽어야 하기 때문이다.
 */
public record SellerProgressDetailInfo(
        Long vehicleId,
        ProgressStage stage,
        String thumbnailUrl,
        Manufacturer manufacturer,
        String model,
        Integer modelYear,
        Integer mileage,
        String plateNumber,
        Long estimatedPrice,
        LocalDateTime appliedAt,

        /** 평가사가 방문하기로 한 날, 방문견적으로 들어온 건에만 있다 */
        LocalDate visitDate,
        /** 반려됐을 때만 채워진다 */
        String rejectReason,

        Long auctionId,
        Long startPrice,
        /** 입찰이 없으면 null이다. 낙찰로 끝난 건에서는 이 값이 낙찰가다 */
        Long currentPrice,
        LocalDateTime startTime,
        LocalDateTime endTime
) {

    public static SellerProgressDetailInfo from(SellerProgressRow row) {
        return new SellerProgressDetailInfo(
                row.vehicleId(),
                row.stage(),
                row.thumbnailUrl(),
                row.manufacturer(),
                row.model(),
                row.modelYear(),
                row.mileage(),
                row.plateNumber(),
                row.estimatedPrice(),
                row.appliedAt(),
                row.visitDate(),
                row.rejectReason(),
                row.auctionId(),
                row.startPrice(),
                row.currentPrice(),
                row.startTime(),
                row.endTime());
    }
}
