package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록의 한 건. 판매자의 "내 신청"과 평가사의 "내 담당"이 같은 형태를 쓴다.
 * <p>
 * 진단 결과와 사진은 없다. 상세에서 받는다.
 */
@Schema(description = "방문견적 신청 요약")
public record EvaluationSummaryResponse(

        @Schema(description = "방문견적 신청 ID. 상세 조회에 쓴다", example = "1")
        Long evaluationId,

        @Schema(description = "신청 상태. APPROVED면 진단이 끝나 출품할 수 있다",
                example = "APPROVED",
                allowableValues = {"REQUESTED", "APPROVED", "REJECTED"})
        String status,

        @Schema(description = "담당 평가사가 정해졌는지. 배정돼도 status는 REQUESTED로 남으므로 "
                + "이 값으로 구분합니다. 누가 오는지는 상세에서 확인합니다",
                example = "true")
        boolean assigned,

        @Schema(description = "이 차량의 최신 경매 상태. 경매 이력이 없으면 null입니다. "
                + "SCHEDULED는 예정, IN_PROGRESS는 진행 중, ENDED는 낙찰 종료, FAILED는 유찰입니다",
                example = "IN_PROGRESS",
                allowableValues = {"SCHEDULED", "IN_PROGRESS", "ENDED", "FAILED"},
                nullable = true)
        AuctionStatus auctionStatus,

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "모델명", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear,

        @Schema(description = "방문 희망 날짜", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소", example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "접수 시각", example = "2026-08-05T15:30:00")
        LocalDateTime requestedAt,

        @Schema(description = "진단을 끝낸 시각(승인 · 반려). 아직 진행 중이면 null입니다. "
                + "평가사 담당 목록의 완료 범위가 이 값의 역순으로 정렬됩니다",
                example = "2026-08-12T18:05:00",
                nullable = true)
        LocalDateTime completedAt
) {

    public static EvaluationSummaryResponse from(EvaluationSummaryInfo info) {
        return new EvaluationSummaryResponse(
                info.evaluationId(), info.status(), info.assigned(), info.auctionStatus(), info.plateNumber(),
                info.manufacturer(), info.model(), info.modelYear(),
                info.visitDate(), info.visitAddress(), info.requestedAt(), info.completedAt());
    }
}
