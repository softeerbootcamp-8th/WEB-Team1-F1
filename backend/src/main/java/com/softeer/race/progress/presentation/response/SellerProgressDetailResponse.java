package com.softeer.race.progress.presentation.response;

import com.softeer.race.progress.application.dto.SellerProgressDetailInfo;
import com.softeer.race.progress.domain.ProgressStage;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "진행 상황 한 건의 상세")
public record SellerProgressDetailResponse(

        @Schema(description = "차량 식별자", example = "1")
        Long vehicleId,

        @Schema(description = "지금 단계", example = "EVALUATION_ASSIGNED")
        ProgressStage stage,

        @Schema(description = "대표 이미지, 없으면 null", example = "https://cdn.race.dev/1.jpg")
        String thumbnailUrl,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 모델명", example = "아반떼 CN7")
        String model,

        @Schema(description = "연식", example = "2022")
        Integer modelYear,

        @Schema(description = "주행거리(km), 평가사 진단 전이면 null", example = "35000")
        Integer mileage,

        @Schema(description = "번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "예상 시세(원), 평가사 진단 전이면 null", example = "10000000")
        Long estimatedPrice,

        @Schema(description = "신청한 시각", example = "2026-08-03T11:20:00")
        LocalDateTime appliedAt,

        @Schema(description = "평가사 방문 예정일, 방문견적으로 신청한 건에만 있다", example = "2026-08-05")
        LocalDate visitDate,

        @Schema(description = "반려 사유, 반려된 건에만 있다", example = "차량 상태 확인이 어렵습니다.")
        String rejectReason,

        @Schema(description = "경매 식별자, 아직 출품 전이면 null", example = "1")
        Long auctionId,

        @Schema(description = "시작가(원), 출품 전이면 null", example = "10000000")
        Long startPrice,

        @Schema(description = "현재가(원), 입찰이 없으면 null. 낙찰로 끝났으면 낙찰가다", example = "11000000")
        Long currentPrice,

        @Schema(description = "입찰이 시작되는 시각", example = "2026-08-03T11:50:00")
        LocalDateTime startTime,

        @Schema(description = "마감 시각", example = "2026-08-03T12:10:00")
        LocalDateTime endTime
) {

    public static SellerProgressDetailResponse from(SellerProgressDetailInfo info) {
        return new SellerProgressDetailResponse(
                info.vehicleId(),
                info.stage(),
                info.thumbnailUrl(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.mileage(),
                info.plateNumber(),
                info.estimatedPrice(),
                info.appliedAt(),
                info.visitDate(),
                info.rejectReason(),
                info.auctionId(),
                info.startPrice(),
                info.currentPrice(),
                info.startTime(),
                info.endTime());
    }
}
