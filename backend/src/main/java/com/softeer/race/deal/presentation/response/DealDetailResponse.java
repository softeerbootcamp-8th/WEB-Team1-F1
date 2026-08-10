package com.softeer.race.deal.presentation.response;

import com.softeer.race.deal.application.dto.DealDetailInfo;
import com.softeer.race.deal.domain.CancellationReason;
import com.softeer.race.deal.domain.DealDetailRow;
import com.softeer.race.deal.domain.DealSide;
import com.softeer.race.deal.domain.DealStatus;
import com.softeer.race.deal.domain.FaultParty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래 상세")
public record DealDetailResponse(

        @Schema(description = "거래 식별자", example = "12")
        Long dealId,

        @Schema(description = "낙찰된 경매 식별자, 경매 결과로 돌아갈 때 쓴다", example = "77")
        Long auctionId,

        @Schema(description = "현재 거래 단계", example = "DEPOSIT_PENDING")
        DealStatus status,

        @Schema(description = "이 거래에서 조회한 사람이 선 쪽", example = "BUYER")
        DealSide mySide,

        @Schema(description = "낙찰 금액", example = "31000000")
        Long finalPrice,

        @Schema(description = "차량 모델명", example = "아반떼 CN7")
        String model,

        @Schema(description = "연식", example = "2022")
        Integer modelYear,

        @Schema(description = "주행거리(km)", example = "35000")
        Integer mileage,

        @Schema(description = "차량 대표 사진, 없으면 null")
        String thumbnailUrl,

        @Schema(description = "상대방 이름, 가운데를 가린다", example = "김*현")
        String counterpartName,

        @Schema(description = "거래가 열린 시각, 낙찰이 확정된 시각과 같다",
                example = "2026-08-09T11:20:00")
        LocalDateTime openedAt,

        @Schema(description = "현재 단계로 넘어온 시각", example = "2026-08-09T11:20:00")
        LocalDateTime statusChangedAt,

        @Schema(description = "취소 사유, 취소되지 않았으면 null", example = "DEPOSIT_TIMEOUT")
        CancellationReason cancellationReason,

        @Schema(description = "취소의 귀책, 보증금 향방이 여기서 갈린다. 취소되지 않았으면 null",
                example = "BUYER")
        FaultParty faultParty,

        @Schema(description = "응답을 만든 서버 시각", example = "2026-08-09T12:00:00")
        LocalDateTime serverTime
) {

    public static DealDetailResponse from(DealDetailInfo info) {
        DealDetailRow detail = info.detail();

        return new DealDetailResponse(
                detail.dealId(),
                detail.auctionId(),
                detail.status(),
                info.mySide(),
                detail.finalPrice(),
                detail.model(),
                detail.modelYear(),
                detail.mileage(),
                detail.thumbnailUrl(),
                detail.counterpartName().value(),
                detail.openedAt(),
                detail.statusChangedAt(),
                detail.cancellationReason(),
                detail.faultParty(),
                info.serverTime());
    }
}
