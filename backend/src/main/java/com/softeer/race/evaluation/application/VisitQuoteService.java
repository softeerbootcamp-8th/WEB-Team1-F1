package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.quote.domain.QuotePolicy;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleLookup;
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
 * 경매글과 경매는 만들지 않는다. 진단이 끝난 뒤에 출품하는 것이 이 흐름의 전제이고, 그래서
 * 판매 신청({@code SellService})과 달리 여기서는 경매가 생기지 않는다. 두 흐름은 지금 병행하며,
 * 진단 완료 → 출품 전환이 붙을 때 판매 신청이 이쪽으로 흡수된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitQuoteService {

    private final VehicleLookup vehicleLookup;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final EvaluationRepository evaluationRepository;
    private final Clock clock;

    /**
     * 번호판으로 제원을 조회해 차량을 등록하고, 배정 대기 상태의 평가 요청을 만든다.
     */
    @Transactional
    public VisitQuoteInfo request(VisitQuoteCommand command) {
        // 중복 검사를 가장 먼저 한다. 같은 트랜잭션이라 뒤에서 던져도 앞의 insert가 롤백되므로
        // 데이터가 남는 문제는 아니지만, 거부될 요청에 카탈로그 조회와 두 번의 insert를 태울 이유가 없다
        //
        // exists 검사와 insert가 원자적이지 않아 동시 요청 두 건이 모두 통과할 수 있다. 부분 unique
        // 인덱스(status가 진행 중인 행만 유일)를 MySQL이 지원하지 않아 DB 제약으로는 막을 수 없고,
        // 락을 걸 대상 행도 없다(막아야 하는 것은 아직 없는 행이다). 저빈도 쓰기이고 배정 단계에서
        // 사람이 걸러낼 수 있어 수용한다. 정말 막아야 하면 vehicle 위가 아닌 "번호판" 단위의
        // 별도 테이블에 unique를 걸어 접수 슬롯을 선점하는 구조가 필요하다
        if (evaluationRepository.existsByVehiclePlateNumberAndStatusIn(
                command.plateNumber(), EvaluationStatus.inProgress())) {
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

        // 날짜는 한 번만 읽어 나이 계산과 방문일 검증에 함께 쓴다. 두 번 읽으면 자정을 넘기는 순간
        // 검증에는 통과한 날짜가 나이 계산에서는 다른 해로 잡히고, 고정 Clock 테스트에서는 재현되지 않는다
        LocalDate today = LocalDate.now(clock);

        long estimatedPrice = QuotePolicy.estimate(spec.basePrice(),
                QuotePolicy.ageOf(spec.modelYear(), today.getYear()), spec.mileage());

        // 번호판 중복은 위에서 상태로만 걸렀다. 반려된 차량은 다시 신청할 수 있어야 하므로
        // 여기서 기존 vehicle 행을 재사용하지 않고 매번 새로 만든다
        Vehicle vehicle = Vehicle.create(seller, spec, estimatedPrice);

        // 저장 전에 조립한다. 방문일 규칙 위반이 Evaluation.request에서 터지므로,
        // 순서를 뒤집으면 거부될 요청이 vehicle insert까지 갔다가 롤백된다
        Evaluation evaluation = Evaluation.request(vehicle, command.visitDate(),
                command.visitAddress(), command.contactPhone(), today);

        vehicleRepository.save(vehicle);

        return VisitQuoteInfo.from(evaluationRepository.save(evaluation), estimatedPrice);
    }
}
