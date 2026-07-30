package com.softeer.race.sell.presentation.response;

import com.softeer.race.sell.application.dto.info.SellApplicationInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 판매 신청 결과. 평가 요청을 만들지 않으므로 auctionId가 신청 식별자 역할을 한다.
 * <p>
 * startAt과 roomOpenAt을 함께 내려 "1시간 뒤 시작, 30분 전 입장"을 응답 하나로 안내한다.
 * plateNumber(클라이언트가 이미 보냈다), ownerName(개인정보 재노출), auctionPostId(쓸 API가 없다)는 넣지 않는다.
 */
@Schema(description = "판매 신청 응답")
public record SellApplicationResponse(

        @Schema(description = "생성된 경매 ID", example = "1")
        Long auctionId,

        @Schema(description = "등록된 차량 ID", example = "1000")
        Long vehicleId,

        @Schema(description = "경매 시작가(조회된 기준가)", example = "24800000")
        long startPrice,

        @Schema(description = "경매 시작 시각", example = "2026-07-30T21:31:00")
        LocalDateTime startAt,

        @Schema(description = "경매방 입장 가능 시각", example = "2026-07-30T21:01:00")
        LocalDateTime roomOpenAt,

        @Schema(description = "경매 마감 시각", example = "2026-07-30T21:51:00")
        LocalDateTime endAt,

        @Schema(description = "경매 상태", example = "SCHEDULED")
        String status
) {

    public static SellApplicationResponse from(SellApplicationInfo info) {
        return new SellApplicationResponse(
                info.auctionId(), info.vehicleId(),
                info.startPrice(), info.startAt(),
                info.roomOpenAt(), info.endAt(),
                info.status());
    }
}
