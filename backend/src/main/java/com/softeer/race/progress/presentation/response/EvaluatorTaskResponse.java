package com.softeer.race.progress.presentation.response;

import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.progress.application.dto.EvaluatorTaskInfo;
import com.softeer.race.progress.domain.EvaluatorTaskGroup;
import com.softeer.race.vehicle.domain.Manufacturer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "평가사가 보는 방문 평가 신청 한 건")
public record EvaluatorTaskResponse(

        @Schema(description = "평가 신청 식별자", example = "1")
        Long evaluationId,

        @Schema(description = "이 신청이 나에게 어떤 일감인지", example = "UNASSIGNED")
        EvaluatorTaskGroup group,

        @Schema(description = "신청 상태", example = "REQUESTED")
        EvaluationStatus status,

        @Schema(description = "방문 희망일", example = "2026-08-05")
        LocalDate visitDate,

        @Schema(description = "방문 희망 장소", example = "서울시 강남구 테헤란로 1")
        String visitAddress,

        @Schema(description = "접수된 시각", example = "2026-08-03T11:20:00")
        LocalDateTime requestedAt,

        @Schema(description = "차량 식별자", example = "1")
        Long vehicleId,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "차량 모델명", example = "아반떼 CN7")
        String model,

        @Schema(description = "연식", example = "2022")
        Integer modelYear,

        @Schema(description = "번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "신청자 이름", example = "김소프티")
        String sellerName
) {

    public static EvaluatorTaskResponse from(EvaluatorTaskInfo info) {
        return new EvaluatorTaskResponse(
                info.evaluationId(),
                info.group(),
                info.status(),
                info.visitDate(),
                info.visitAddress(),
                info.requestedAt(),
                info.vehicleId(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.plateNumber(),
                info.sellerName());
    }
}
