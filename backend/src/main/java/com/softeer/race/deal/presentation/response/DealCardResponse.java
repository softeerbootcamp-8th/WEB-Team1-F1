package com.softeer.race.deal.presentation.response;

import com.softeer.race.deal.application.dto.DealCardInfo;
import com.softeer.race.deal.domain.DealListRow;
import com.softeer.race.deal.domain.DealSide;
import com.softeer.race.deal.domain.DealStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래 목록 카드 한 건")
public record DealCardResponse(

        @Schema(description = "거래 식별자", example = "12")
        Long dealId,

        @Schema(description = "낙찰된 경매 식별자", example = "77")
        Long auctionId,

        @Schema(description = "현재 거래 단계", example = "SELLER_SUBMIT_PENDING")
        DealStatus status,

        @Schema(description = "이 거래에서 조회한 사람이 선 쪽, 화면이 액션 버튼을 가르는 기준",
                example = "BUYER")
        DealSide mySide,

        @Schema(description = "낙찰 금액, 낙찰 순간에 고정된다", example = "31000000")
        Long finalPrice,

        @Schema(description = "차량 모델명", example = "아반떼 CN7")
        String model,

        @Schema(description = "차량 대표 사진, 없으면 null")
        String thumbnailUrl,

        @Schema(description = "상대방 이름, 가운데를 가린다", example = "김*현")
        String counterpartName,

        @Schema(description = "현재 단계로 넘어온 시각, 카드의 상대 시각 표시에 쓴다",
                example = "2026-08-09T12:00:00")
        LocalDateTime statusChangedAt,

        @Schema(description = "지금 내가 움직일 차례인지, 목록에서 액션 배지를 켜는 기준", example = "true")
        boolean actionRequired
) {

    public static DealCardResponse from(DealCardInfo info) {
        DealListRow card = info.card();

        return new DealCardResponse(
                card.dealId(),
                card.auctionId(),
                card.status(),
                info.mySide(),
                card.finalPrice(),
                card.model(),
                card.thumbnailUrl(),
                card.counterpartName().value(),
                card.statusChangedAt(),
                card.actionRequiredFor(info.viewerId()));
    }
}
