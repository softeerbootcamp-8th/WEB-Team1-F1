package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 배정 대기 목록의 한 건. 평가사가 이 신청을 맡을지 판단할 재료만 담는다.
 * <p>
 * <b>contactPhone을 담지 않는다.</b> 이 목록은 평가사 전원이 보므로, 넣으면 결국 배정받지 않을
 * 사람들에게까지 판매자 전화번호가 뿌려진다. 연락처는 배정이 확정된 뒤
 * {@link EvaluationAssignmentInfo}로만 나간다.
 * <p>
 * visitAddress는 반대로 담는다. 개인정보인 것은 같지만 "갈 수 있는 곳인가"가 수락 판단의 핵심이라
 * 빼면 목록으로 아무것도 판단할 수 없다. 도 · 시 단위로 잘라 보여주는 방법도 있지만, 같은 시 안에서도
 * 이동 시간이 갈려 판단 근거가 되지 못한다.
 * <p>
 * status를 담지 않는다. 이 목록에 오르는 조건 자체가 REQUESTED라 항목마다 같은 값이 반복된다.
 * <p>
 * 주행거리와 예상 시세도 담지 않는다. 진단 전 차량은 두 값이 비어 있다({@code Vehicle.mileage}) —
 * 그 값을 채우는 것이 평가사가 방문해서 할 일이다.
 */
public record AssignableEvaluationInfo(
        Long evaluationId,
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        FuelType fuelType,
        Transmission transmission,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt
) {

    public static AssignableEvaluationInfo from(Evaluation evaluation) {
        Vehicle vehicle = evaluation.getVehicle();

        return new AssignableEvaluationInfo(
                evaluation.getId(),
                vehicle.getPlateNumber(),
                vehicle.getManufacturer(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                vehicle.getFuelType(),
                vehicle.getTransmission(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                // 접수 시각이다. 오래 대기한 신청을 평가사가 알아볼 수 있어야 한다
                evaluation.getCreatedAt()
        );
    }
}
