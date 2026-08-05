package com.softeer.race.progress.application.dto;

import com.softeer.race.progress.domain.ProgressStage;
import com.softeer.race.progress.domain.SellerProgressRow;
import com.softeer.race.vehicle.domain.Manufacturer;

import java.time.LocalDateTime;

/**
 * 진행 상황 목록의 한 줄
 * <p>
 * 단계마다 다른 값을 담지 않고 어느 단계에서나 같은 자리를 채운다. 방문 날짜나 낙찰가처럼
 * 특정 단계에서만 의미가 있는 값은 상세({@link SellerProgressDetailInfo})가 맡는다.
 */
public record SellerProgressInfo(
        Long vehicleId,
        ProgressStage stage,
        String thumbnailUrl,
        Manufacturer manufacturer,
        String model,
        Integer modelYear,
        Integer mileage,
        LocalDateTime appliedAt,
        /** 아직 출품 전이면 null. 경매방으로 갈 수 있는지가 이 값으로 갈린다 */
        Long auctionId
) {

    public static SellerProgressInfo from(SellerProgressRow row) {
        return new SellerProgressInfo(
                row.vehicleId(),
                row.stage(),
                row.thumbnailUrl(),
                row.manufacturer(),
                row.model(),
                row.modelYear(),
                row.mileage(),
                row.appliedAt(),
                row.auctionId());
    }
}
