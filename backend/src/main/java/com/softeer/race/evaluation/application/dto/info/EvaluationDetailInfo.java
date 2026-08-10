package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 신청 한 건의 전부. 목록에서 뺀 진단 결과가 여기 들어온다.
 * <p>
 * <b>진단 전에는 결과 칸이 전부 비어 나간다.</b> mileage · estimatedPrice · diagnosticReportUrl ·
 * submittedAt이 null이고 keywords는 빈 목록이며, imageUrls에는 판매 신청이 넣어 둔 카탈로그 이미지만
 * 있다. 그 비어 있음이 status와 함께 "아직 평가사가 다녀가지 않았다"를 뜻한다.
 * <p>
 * keywords만 null이 아니라 빈 목록인 것은 진단을 마친 차량도 키워드가 0개일 수 있어서다. null로
 * 구분하려 해도 두 경우가 겹쳐 구분되지 않는다.
 * <p>
 * 원시 타입을 쓰지 않고 {@code Integer} · {@code Long}으로 받는 이유가 그것이다. 진단 전 차량은
 * 두 값이 실제로 비어 있어({@code Vehicle.pendingDiagnosis}) 원시 타입으로 받으면 언박싱에서 터진다.
 * <p>
 * <b>contactPhone을 담는다.</b> 배정 응답({@link EvaluationAssignmentInfo})이 한 번 주고 끝이라,
 * 평가사가 그 화면을 닫으면 방문 전에 연락할 번호를 다시 찾을 데가 없었다. 여기는 이미 판매자와
 * 배정 평가사로 열람이 좁혀져 있어 제3자에게 새지 않는다.
 * <p>
 * 요청자가 판매자일 때 비우지 않는다. 자기가 적은 번호라 되돌려줘도 새는 것이 아니고, 요청자에
 * 따라 채우고 비우면 같은 엔드포인트의 응답 형태가 사람마다 갈린다. 판매자 화면은 그냥 쓰지 않으면 된다.
 * <p>
 * <b>이미 출품했는지는 알려주지 않는다.</b> 그러려면 경매를 봐야 하는데, 무엇을 "출품됨"으로 볼지의
 * 기준({@code AuctionService.ACTIVE_STATUSES})이 그쪽에 private으로 있어 이 패키지에서 다시 정의하면
 * 같은 규칙이 두 곳에 생긴다. 한쪽에만 상태를 더하면 같은 차량이 화면마다 다르게 보인다.
 * 경매 쪽이 그 기준을 열어 줄 때 담는다.
 */
public record EvaluationDetailInfo(
        Long evaluationId,
        String status,
        LocalDate visitDate,
        String visitAddress,
        String contactPhone,
        LocalDateTime requestedAt,

        String evaluatorName,

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
        LocalDateTime submittedAt,
        List<VehicleKeyword> keywords
) {

    public static EvaluationDetailInfo of(Evaluation evaluation,
                                          List<VehicleImage> images,
                                          List<VehicleKeyword> keywords) {
        Vehicle vehicle = evaluation.getVehicle();
        User evaluator = evaluation.getEvaluator();

        return new EvaluationDetailInfo(
                evaluation.getId(),
                evaluation.getStatus().name(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getContactPhone(),
                evaluation.getCreatedAt(),

                // 배정 전에는 비어 있다. 그 null이 "아직 담당자가 정해지지 않았다"를 뜻한다
                evaluator == null ? null : evaluator.getRealName(),

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
                vehicle.getDiagnosticReportUrl(),
                // 제출 시각은 차량이 결과로 채워진 때다. 평가의 updatedAt은 배정에도 움직여
                // "결과가 올라온 때"를 가리키지 못한다
                vehicle.isDiagnosed() ? vehicle.getUpdatedAt() : null,
                // 진단 전에는 비어 있다. submittedAt이 null인 것과 같은 뜻이다
                keywords);
    }
}
