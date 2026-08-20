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

        @Schema(description = "현재 거래 단계", example = "SELLER_SUBMIT_PENDING")
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

        @Schema(description = "취소 사유, 취소되지 않았으면 null", example = "BUYER_CANCELLED")
        CancellationReason cancellationReason,

        @Schema(description = "취소의 귀책이 어느 쪽인지. 취소되지 않았으면 null",
                example = "BUYER")
        FaultParty faultParty,

        @Schema(description = "판매자가 낸 명의이전 서류 PDF 주소, 아직 없으면 null")
        String documentUrl,

        @Schema(description = "탁송 출발 일시, 아직 없으면 null",
                example = "2026-08-20T14:00:00")
        LocalDateTime transportAt,

        @Schema(description = "탁송 출발지, 아직 없으면 null", example = "서울시 강남구 테헤란로 123")
        String transportLocation,

        @Schema(description = "차량 인수 일시, 아직 없으면 null",
                example = "2026-08-21T10:00:00")
        LocalDateTime deliveryAt,

        @Schema(description = "차량 인수 장소, 아직 없으면 null", example = "부산시 해운대구 센텀중앙로 55")
        String deliveryLocation,

        @Schema(description = "지금 내가 움직일 차례인지, 화면이 액션 버튼을 켜는 기준", example = "true")
        boolean actionRequired,

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
                detail.documentUrl(),
                detail.transportAt(),
                detail.transportLocation(),
                detail.deliveryAt(),
                detail.deliveryLocation(),
                detail.actionRequiredFor(info.viewerId()),
                info.serverTime());
    }
}
