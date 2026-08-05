package com.softeer.race.progress.presentation.response;

import com.softeer.race.progress.application.dto.SellerProgressInfo;
import com.softeer.race.progress.domain.ProgressStage;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내가 낸 신청 한 건의 진행 상황")
public record SellerProgressResponse(

        @Schema(description = "차량 식별자, 상세 조회에 쓴다", example = "1")
        Long vehicleId,

        @Schema(description = "지금 단계", example = "AUCTION_LIVE")
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

        @Schema(description = "신청한 시각", example = "2026-08-03T11:20:00")
        LocalDateTime appliedAt,

        @Schema(description = "경매 식별자, 아직 출품 전이면 null", example = "1")
        Long auctionId
) {

    public static SellerProgressResponse from(SellerProgressInfo info) {
        return new SellerProgressResponse(
                info.vehicleId(),
                info.stage(),
                info.thumbnailUrl(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.mileage(),
                info.appliedAt(),
                info.auctionId());
    }
}
