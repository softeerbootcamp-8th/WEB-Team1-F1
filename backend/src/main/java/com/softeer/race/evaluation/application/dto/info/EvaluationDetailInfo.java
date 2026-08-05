package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 신청 한 건의 전부. 목록에서 뺀 진단 결과가 여기 들어온다.
 * <p>
 * <b>진단 전에는 결과 칸이 전부 비어 나간다.</b> mileage · estimatedPrice · diagnosticReportUrl ·
 * submittedAt이 null이고 imageUrls에는 판매 신청이 넣어 둔 카탈로그 이미지만 있다. 그 비어 있음이
 * status와 함께 "아직 평가사가 다녀가지 않았다"를 뜻한다.
 * <p>
 * 원시 타입을 쓰지 않고 {@code Integer} · {@code Long}으로 받는 이유가 그것이다. 진단 전 차량은
 * 두 값이 실제로 비어 있어({@code Vehicle.pendingDiagnosis}) 원시 타입으로 받으면 언박싱에서 터진다.
 * <p>
 * <b>contactPhone을 담지 않는다.</b> 판매자에게는 자기가 방금 적은 번호라 되돌려줄 이유가 없고,
 * 평가사에게는 배정 확정 시 {@link EvaluationAssignmentInfo}가 이미 준다. 요청자에 따라 채우고
 * 비우면 같은 엔드포인트의 응답 형태가 사람마다 갈린다.
 */
public record EvaluationDetailInfo(
        Long evaluationId,
        String status,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt,

        Long vehicleId,
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        FuelType fuelType,
        Transmission transmission,

        Integer mileage,
        Long estimatedPrice,
        List<String> imageUrls,
        String diagnosticReportUrl,
        LocalDateTime submittedAt
) {

    /**
     * @param report 아직 제출되지 않았으면 null이다
     */
    public static EvaluationDetailInfo of(Evaluation evaluation,
                                          List<VehicleImage> images,
                                          DiagnosticReport report) {
        Vehicle vehicle = evaluation.getVehicle();

        return new EvaluationDetailInfo(
                evaluation.getId(),
                evaluation.getStatus().name(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getCreatedAt(),

                vehicle.getId(),
                vehicle.getPlateNumber(),
                vehicle.getManufacturer(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                vehicle.getFuelType(),
                vehicle.getTransmission(),

                vehicle.getMileage(),
                vehicle.getEstimatedPrice(),
                images.stream().map(VehicleImage::getImageUrl).toList(),
                report == null ? null : report.getFileUrl(),
                // 제출 시각은 진단서 행의 갱신 시각이다. 평가의 updatedAt은 배정에도 움직여
                // "결과가 올라온 때"를 가리키지 못한다
                report == null ? null : report.getUpdatedAt());
    }
}
