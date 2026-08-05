package com.softeer.race.evaluation.presentation.response;

import com.softeer.race.evaluation.application.dto.info.EvaluationDetailInfo;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 신청 한 건의 상세.
 * <p>
 * <b>진단 전에는 mileage · estimatedPrice · diagnosticReportUrl · submittedAt이 null이다.</b>
 * status와 함께 읽으면 "아직 평가사가 다녀가지 않았다"를 뜻한다. imageUrls에는 그 시점에도
 * 카탈로그 이미지가 들어 있어 비어 있지 않을 수 있다.
 * <p>
 * 연락처는 담지 않는다. 판매자에게는 자기가 적은 번호이고, 평가사는 배정될 때 이미 받는다.
 */
@Schema(description = "방문견적 신청 상세 응답")
public record EvaluationDetailResponse(

        @Schema(description = "방문견적 신청 ID", example = "1")
        Long evaluationId,

        @Schema(description = "신청 상태", example = "DIAGNOSED",
                allowableValues = {"REQUESTED", "APPROVED", "DIAGNOSED", "REJECTED"})
        String status,

        @Schema(description = "방문 희망 날짜", example = "2026-08-20")
        LocalDate visitDate,

        @Schema(description = "방문 주소", example = "서울 성동구 왕십리로 83")
        String visitAddress,

        @Schema(description = "접수 시각", example = "2026-08-05T15:30:00")
        LocalDateTime requestedAt,

        @Schema(description = "차량 ID. 출품할 때 쓴다", example = "1000")
        Long vehicleId,

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

        @Schema(description = "평가사가 실측한 주행거리(km). 진단 전에는 null입니다", example = "45000")
        Integer mileage,

        @Schema(description = "평가사가 산정한 예상 시세(원). 진단 전에는 null입니다",
                example = "21500000")
        Long estimatedPrice,

        @Schema(description = "차량 사진. 진단 전에는 카탈로그 이미지가 들어 있을 수 있습니다")
        List<String> imageUrls,

        @Schema(description = "진단서 PDF 주소. 진단 전에는 null입니다",
                example = "https://www.f1race.site/documents/2026/08/"
                        + "3f2b1c8e-0d47-4a19-9b2f-6c1d5e7a8b90.pdf")
        String diagnosticReportUrl,

        @Schema(description = "결과가 제출된 시각. 진단 전에는 null입니다",
                example = "2026-08-05T18:00:00")
        LocalDateTime submittedAt
) {

    public static EvaluationDetailResponse from(EvaluationDetailInfo info) {
        return new EvaluationDetailResponse(
                info.evaluationId(), info.status(), info.visitDate(),
                info.visitAddress(), info.requestedAt(),

                info.vehicleId(), info.plateNumber(), info.manufacturer(),
                info.model(), info.modelYear(), info.fuelType(), info.transmission(),

                info.mileage(), info.estimatedPrice(), info.imageUrls(),
                info.diagnosticReportUrl(), info.submittedAt());
    }
}
