package com.softeer.race.evaluation.application.dto.info;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Vehicle;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록의 한 건. 판매자의 "내 신청"과 평가사의 "내 담당"이 같은 형태를 쓴다 — 두 화면이 묻는 것이
 * "이 신청이 지금 어디까지 왔고 언제 어디로 가는가"로 같다.
 * <p>
 * <b>진단 결과를 담지 않는다.</b> 주행거리 · 시세 · 사진은 상세에서만 나간다. 목록에 넣으려면
 * 진단서와 사진을 건수만큼 더 읽어야 하는데, 목록에서 그 값들로 할 수 있는 판단이 없다.
 * <p>
 * 그래서 {@code status}가 이 목록의 핵심이다. 판매자는 APPROVED를 보고 출품으로 넘어가고,
 * 평가사는 REQUESTED를 보고 아직 방문하지 않은 건을 가려낸다.
 * <p>
 * <b>{@code assigned}가 없으면 판매자 화면이 멈춰 보인다.</b> 배정돼도 상태는 REQUESTED 그대로라
 * (배정과 평가 결과가 다른 축이라는 설계), 이 값이 없으면 접수 직후와 평가사가 정해진 뒤가
 * 화면에서 똑같다. 판매자는 전화가 오기 전까지 아무 일도 일어나지 않는 줄 안다.
 * <p>
 * 평가사 이름까지는 담지 않는다. 목록에서 할 판단은 "진행됐는가"뿐이고, 누가 오는지는 상세를
 * 열어 확인한다.
 * <p>
 * <b>최신 경매 상태를 담는다.</b> 유찰 뒤 재출품할 수 있으므로 경매 이력의 존재만으로는 현재
 * 상황을 말할 수 없다. 경매가 없으면 null이고, 있으면 가장 최근 경매의 상태다.
 * <p>
 * <b>contactPhone을 담지 않는다.</b> 담당 평가사에게는 필요한 값이지만 배정이 확정될 때
 * {@link EvaluationAssignmentInfo}가 이미 준다. 목록마다 다시 실어 나르면 개인정보가 로그와
 * 캐시에 남는 면만 넓어진다.
 */
public record EvaluationSummaryInfo(
        Long evaluationId,
        String status,
        boolean assigned,
        AuctionStatus auctionStatus,
        String plateNumber,
        Manufacturer manufacturer,
        String model,
        int modelYear,
        LocalDate visitDate,
        String visitAddress,
        LocalDateTime requestedAt
) {

    public static EvaluationSummaryInfo from(Evaluation evaluation, AuctionStatus auctionStatus) {
        Vehicle vehicle = evaluation.getVehicle();

        return new EvaluationSummaryInfo(
                evaluation.getId(),
                evaluation.getStatus().name(),
                // null 검사는 프록시를 초기화하지 않는다. 이름을 읽었다면 건수만큼 쿼리가 늘었을 것이다
                evaluation.getEvaluator() != null,
                auctionStatus,
                vehicle.getPlateNumber(),
                vehicle.getManufacturer(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                evaluation.getVisitDate(),
                evaluation.getVisitAddress(),
                evaluation.getCreatedAt());
    }
}
