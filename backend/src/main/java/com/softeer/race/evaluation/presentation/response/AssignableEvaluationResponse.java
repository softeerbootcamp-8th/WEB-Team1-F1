package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 배정 대기 목록의 한 건.
 * <p>
 * 연락처는 없다. 이 목록은 평가사 전원이 보므로 배정받지 않을 사람들에게까지 판매자 전화번호가
 * 뿌려진다. 배정에 성공하면 {@link EvaluationAssignmentResponse}로 받는다.
 */
@Schema(description = "배정 대기 중인 방문견적 신청")
public record AssignableEvaluationResponse(

        @Schema(description = "방문견적 신청 ID, 수락 요청에 쓴다", example = "1")
        Long evaluationId,

        @Schema(description = "차량 번호판", example = "12가3456")
        String plateNumber,

        @Schema(description = "제조사", example = "HYUNDAI")
        Manufacturer manufacturer,

        @Schema(description = "모델명", example = "그랜저 IG")
        String model,

        @Schema(description = "연식", example = "2021")
        int modelYear,

        @Schema(description = "연료", example = "GASOLINE")
        FuelType fuelType,

        @Schema(description = "변속기", example = "AUTOMATIC")
        Transmission transmission,

        @Schema(description = "판매자가 희망한 방문 날짜. 목록은 이 날짜가 임박한 순서다",
                example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소. 갈 수 있는 곳인지가 수락 판단의 핵심이라 목록에도 담는다",
                example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "신청이 접수된 시각. 오래 대기한 건을 알아볼 수 있게 한다",
                example = "2026-08-04T11:20:00")
        LocalDateTime requestedAt
) {

    public static AssignableEvaluationResponse from(AssignableEvaluationInfo info) {
        return new AssignableEvaluationResponse(
                info.evaluationId(),
                info.plateNumber(),
                info.manufacturer(),
                info.model(),
                info.modelYear(),
                info.fuelType(),
                info.transmission(),
                info.visitDate(),
                info.visitAddress(),
                info.requestedAt());
    }
}
