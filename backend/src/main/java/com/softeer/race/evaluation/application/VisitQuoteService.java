package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.evaluation.application.dto.info.VisitQuotePrecheckInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.PlateNumberLockRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.notification.domain.NotificationContent;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.application.dto.command.VehicleLookupCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleLookupInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleName;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.domain.VehicleSpec;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문견적 신청 접수. 예상 시세를 확인한 판매자가 방문 희망 장소 · 날짜 · 연락처를 보내면
 * 차량과 평가 요청을 한 트랜잭션으로 만들고 평가사 배정 대기 상태로 둔다.
 * <p>
 * <b>예상 시세를 산정하지 않는다.</b> 이 접수는 "이 차를 봐 주세요"라는 예약이고, 주행거리 실측과
 * 시세 산정은 평가사가 방문해서 하는 일이다. 여기서 금액을 계산해 응답에 실으면 사용자는 그것을
 * 평가사가 제시할 금액으로 읽는데, 주행거리를 모르는 상태의 계산은 아무것도 보증하지 않는다.
 * 그래서 차량은 주행거리 · 예상 시세가 빈 상태로 만들어진다({@code Vehicle.pendingDiagnosis}).
 * <p>
 * 경매글과 경매는 만들지 않는다. 진단이 끝난 뒤에 출품하는 것이 이 흐름의 전제라, 접수는 차량과
 * 평가 요청까지만 만들고 경매는 판매자가 결과를 확인한 뒤 {@code AuctionService.create}로 만든다.
 * <p>
 * 예전에는 번호판과 주행거리를 받아 경매까지 한 번에 만드는 판매 신청이 함께 있었다. 평가사를
 * 거치지 않아 주행거리가 검증되지 않았고, 이 흐름이 자리 잡으면서 흡수돼 사라졌다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitQuoteService {

    private final VehicleLookup vehicleLookup;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final EvaluationRepository evaluationRepository;
    private final PlateNumberLockRepository plateNumberLockRepository;
    private final PlateNumberLockCreator plateNumberLockCreator;
    private final NotificationPublisher notificationPublisher;
    private final Clock clock;

    /**
     * 차량 확인 뒤 예약 화면으로 이동해도 되는지 확인한다.
     * <p>
     * 공개 엔드포인트에서 쓰므로 소유자명까지 맞는 차량인지 먼저 확인한 뒤 중복 여부를 조회한다.
     * 순서를 뒤집으면 번호판만 대입해 진행 중인 방문견적의 존재를 알아낼 수 있다.
     */
    public VisitQuotePrecheckInfo precheck(VehicleLookupCommand command) {
        VehicleSpec spec = vehicleLookup.find(command.plateNumber(), command.ownerName())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.VEHICLE_NOT_FOUND));

        boolean hasInProgressVisitQuote =
                evaluationRepository.existsBlockingVisitQuoteByPlateNumber(spec.plateNumber());

        return new VisitQuotePrecheckInfo(
                VehicleLookupInfo.from(spec), hasInProgressVisitQuote);
    }

    /**
     * 번호판으로 제원을 조회해 차량을 등록하고, 배정 대기 상태의 평가 요청을 만든다.
     */
    @Transactional
    public VisitQuoteInfo request(VisitQuoteCommand command) {
        // 중복 판정보다 앞에 잠금이 온다. 판정과 insert 사이가 원자적이지 않아, 잠그지 않으면 거의
        // 같은 순간에 들어온 두 요청이 모두 "진행 중인 신청 없음"을 읽고 모두 접수된다.
        // 잠글 대상 행이 왜 따로 필요한지는 PlateNumberLock,
        // 확보가 왜 별도 트랜잭션인지는 PlateNumberLockCreator 참고
        plateNumberLockCreator.createIfAbsent(command.plateNumber());
        plateNumberLockRepository.findByPlateNumberForUpdate(command.plateNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "직전에 확보한 번호판 잠금 행을 찾을 수 없다, 번호판 %s".formatted(command.plateNumber())));

        // 중복 검사를 나머지 작업보다 먼저 한다. 같은 트랜잭션이라 뒤에서 던져도 앞의 insert가
        // 롤백되므로 데이터가 남는 문제는 아니지만, 거부될 요청에 카탈로그 조회와 두 번의 insert를
        // 태울 이유가 없다. 앞에 붙은 잠금 두 쿼리는 그 "비싼 작업"에 들지 않는다
        if (evaluationRepository.existsBlockingVisitQuoteByPlateNumber(command.plateNumber())) {
            throw new BusinessException(EvaluationErrorCode.DUPLICATE_REQUEST);
        }

        // 제원은 클라이언트가 아니라 서버가 조회한다. 클라이언트 값을 믿으면 연식·주행거리를 위조해
        // 예상 시세를 부풀릴 수 있다.
        //
        // 인증된 요청인데도 소유자명까지 대조하는 find를 쓴다. 세션은 요청자가 누구인지만 증명하고
        // 그 차가 그 사람 것인지는 증명하지 않으므로, 번호판만 받으면 로그인한 아무나 카탈로그의
        // 임의 번호판으로 평가사 방문을 잡을 수 있다. 소유자명은 시세 조회 단계에서 이미 입력한 값이다
        VehicleSpec spec = vehicleLookup.find(command.plateNumber(), command.ownerName())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.VEHICLE_NOT_FOUND));

        // getReferenceById가 아니라 findById를 쓴다. 존재 확인을 flush로 미루면 계정이 사라진 경우
        // vehicle.seller_id FK 위반 → DataIntegrityViolationException → 최후방 핸들러의 500이 된다
        User seller = userRepository.findById(command.sellerId())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.SELLER_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);

        // 번호판 중복은 위에서 상태로만 걸렀다. 반려된 차량은 다시 신청할 수 있어야 하므로
        // 여기서 기존 vehicle 행을 재사용하지 않고 매번 새로 만든다
        //
        // spec 에서 쓰는 것은 제조사 · 모델 · 연식 · 연료 · 변속기다. 그 컬럼들이 NOT NULL 이라
        // 조회를 건너뛸 수는 없다. basePrice 는 쓰지 않는다 — 시세를 산정하지 않으므로 필요가 없다
        Vehicle vehicle = Vehicle.pendingDiagnosis(seller, spec);

        // 저장 전에 조립한다. 방문일 규칙 위반이 Evaluation.request에서 터지므로,
        // 순서를 뒤집으면 거부될 요청이 vehicle insert까지 갔다가 롤백된다
        Evaluation evaluation = Evaluation.request(vehicle, command.visitDate(),
                command.visitAddress(), command.contactPhone(), today);

        vehicleRepository.save(vehicle);
        Evaluation saved = evaluationRepository.save(evaluation);

        notifyEvaluators(saved.getId(), vehicle);

        return VisitQuoteInfo.from(saved);
    }

    /**
     * 접수된 신청을 신청 당시의 평가사 전원에게 알린다. 평가사가 없으면 아무 일도 하지 않는다.
     */
    private void notifyEvaluators(long evaluationId, Vehicle vehicle) {
        // 문구는 수신자와 무관하게 같아 한 번만 조립한다
        NotificationContent content = NotificationContent.evaluationRequested(
                VehicleName.of(vehicle).display(), vehicle.getPlateNumber());

        for (long evaluatorId : userRepository.findIdsByRole(Role.EVALUATOR)) {
            notificationPublisher.publishContent(evaluatorId, content, evaluationId);
        }
    }
}
