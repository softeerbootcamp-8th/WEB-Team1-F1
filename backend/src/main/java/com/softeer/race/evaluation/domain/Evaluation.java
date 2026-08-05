package com.softeer.race.evaluation.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 낸 평가사 방문 요청. 접수되면 평가사가 배정될 때까지 {@code REQUESTED} + {@code evaluator == null}로 대기한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /**
     * 배정 전에는 비어 있다. 배정 대기를 별도 상태 상수로 두지 않고 이 필드의 null로 표현한다 —
     * 상태를 하나 더 만들면 "REQUESTED이지만 배정됨"과 "배정 대기"가 두 곳에서 관리돼 어긋날 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id")
    private User evaluator;

    /**
     * 날짜만 받는다. 몇 시에 방문할지는 평가사가 배정된 뒤 협의할 일이라 신청 시점에 정할 수 없고,
     * LocalDateTime으로 두면 서버가 임의의 기본 시각을 만들어 넣게 된다.
     */
    @Column(nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false)
    private String visitAddress;

    /**
     * 방문 시 연락받을 번호. {@code User.phone}으로 대신하지 않는다. 가입 때 적은 번호와 방문 연락을
     * 받을 번호가 다를 수 있고, 회원이 나중에 번호를 바꿔도 이 신청 당시의 값이 남아야 한다.
     * <p>
     * 하이픈 없는 숫자만 저장된다(형식 강제는 요청 DTO가 한다). 응답으로는 나가지 않는다.
     */
    @Column(nullable = false)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status;

    private String rejectReason;

    private Evaluation(Vehicle vehicle, LocalDate visitDate, String visitAddress, String contactPhone) {
        this.vehicle = vehicle;
        this.visitDate = visitDate;
        this.visitAddress = visitAddress;
        this.contactPhone = contactPhone;
        this.status = EvaluationStatus.REQUESTED;
    }

    /**
     * 방문 희망 장소 · 날짜 · 연락처로 신청을 접수한다. 평가사는 배정되지 않은 상태로 남는다.
     * <p>
     * 오늘 날짜를 파라미터로 받는 이유는 엔티티가 Clock 빈을 주입받을 수 없고, 저장소 규칙상
     * {@code LocalDate.now()}를 직접 호출할 수 없기 때문이다. 검증을 서비스로 올리지 않는 것은
     * {@code Auction.schedule}이 최소 리드타임을 스스로 검증하는 것과 같은 이유다 — 규칙을 어긴
     * 인스턴스가 애초에 만들어지지 않아야 다른 호출자가 생겨도 검증이 빠지지 않는다.
     */
    public static Evaluation request(Vehicle vehicle, LocalDate visitDate,
                                     String visitAddress, String contactPhone, LocalDate today) {
        validateVisitDate(visitDate, today);

        return new Evaluation(vehicle, visitDate, visitAddress, contactPhone);
    }

    /**
     * 이 사람이 진단 결과를 붙일 수 있는지.
     * <p>
     * 반려된 평가를 막는 것은 그 신청이 이미 끝났기 때문이다. 끝난 신청에 진단 결과가 붙으면
     * 상태와 데이터가 어긋나고, 반려 후 재신청으로 생긴 새 평가와 어느 쪽이 유효한지 알 수 없다.
     * <p>
     * {@link #assignTo}와 상태 조건이 다르다. 배정은 {@code REQUESTED}만 받지만 진단서는
     * {@code APPROVED}에도 붙을 수 있다 — 승인은 방문이 확정됐다는 뜻이고, 진단 결과는 그 방문의
     * 산물이라 오히려 그때 올라온다. 두 규칙을 한 검사로 합치면 승인된 신청에 진단서를 못 붙인다.
     * <p>
     * <b>배정을 자격의 증명으로 쓴다.</b> {@code Role.EVALUATOR}인지 따로 묻지 않는다. 진단서를
     * 붙이려면 이 신청에 배정돼 있어야 하고, 배정은 대기 목록에서 수락해야 받는다. 역할 검사를
     * 여기 더하면 같은 규칙이 배정과 첨부 두 곳에 생기고, 배정이 역할을 보게 되는 날 한쪽만
     * 고쳐 어긋난다. 자격을 봐야 할 자리는 <b>배정하는 곳 한 군데</b>다.
     * <p>
     * 배정 전(evaluator == null)과 남의 담당을 갈라 던진다. 앞쪽은 대기 목록에서 수락하면 풀리고
     * 뒤쪽은 그렇게 해도 풀리지 않아, 화면이 안내할 말이 다르다.
     *
     * @throws BusinessException 반려되어 끝난 평가면 409({@code NOT_DIAGNOSABLE}),
     *                           아직 담당자가 없으면 409({@code EVALUATOR_NOT_ASSIGNED}),
     *                           다른 평가사의 담당이면 403({@code NOT_ASSIGNED_EVALUATOR})
     */
    /**
     * 방문 결과가 제출됐다.
     * <p>
     * {@link #validateDiagnosableBy}와 나눠 둔다. 검증하는 메서드가 상태까지 바꾸면 이름이
     * 거짓이 되고, 검증만 하고 싶은 호출자가 생겼을 때 부작용을 피할 방법이 없다.
     * <p>
     * 여기서 다시 검증하지 않는다. 이 메서드를 부르기 직전에 {@code validateDiagnosableBy}가
     * 통과했다는 것이 전제다 — 같은 검사를 두 번 하면 어느 쪽이 진짜 관문인지 흐려진다.
     * <p>
     * 이미 DIAGNOSED인 상태로 다시 불려도 그대로 둔다. 재제출은 결과를 갈아 끼우는 것이지
     * 상태를 되돌리거나 새로 만드는 것이 아니다.
     */
    public void diagnose() {
        this.status = EvaluationStatus.DIAGNOSED;
    }

    public void validateDiagnosableBy(long userId) {
        // 상태를 먼저 본다. assignTo와 같은 순서다 — 끝난 신청은 누가 물어도 답이 같아,
        // 담당자부터 따지면 같은 상황이 요청자에 따라 403과 409로 갈린다
        if (!EvaluationStatus.inProgress().contains(status)) {
            throw new BusinessException(EvaluationErrorCode.NOT_DIAGNOSABLE);
        }
        if (evaluator == null) {
            throw new BusinessException(EvaluationErrorCode.EVALUATOR_NOT_ASSIGNED);
        }
        if (!evaluator.getId().equals(userId)) {
            throw new BusinessException(EvaluationErrorCode.NOT_ASSIGNED_EVALUATOR);
        }
    }

    // 오늘은 허용한다. 당일 방문 요청 자체가 무의미한 입력은 아니고, 배정 단계에서 판단할 일이다.
    // 상한(예: 90일 이내)은 두지 않는다 — 근거 있는 값을 정할 수 없어 임의의 숫자가 된다
    private static void validateVisitDate(LocalDate visitDate, LocalDate today) {
        if (visitDate.isBefore(today)) {
            throw new BusinessException(EvaluationErrorCode.PAST_VISIT_DATE);
        }
    }

    /**
     * 이 신청을 수락한 평가사를 담당으로 확정한다. 먼저 수락한 한 명만 성립한다.
     * <p>
     * 배정 시각을 따로 저장하지 않는다. {@code BaseTimeEntity.updatedAt}이 그 시각이고, 배정이
     * 취소되지 않으므로 이 필드가 채워진 뒤 이 행이 다시 바뀌는 것은 평가 결과 제출뿐이다.
     * 그때 갱신되면 낡는 값을 하나 더 들고 있게 되므로, 배정 시각이 따로 필요해지는 시점에
     * 컬럼을 만든다.
     * <p>
     * <b>평가사 자격은 검사하지 않는다.</b> 호출자가 확인한다. 배정하려면 {@code User} 엔티티를
     * 조회해야 하니 서비스가 어차피 역할을 볼 수 있고, 배정 대기 목록 조회도 같은 검사를 쓰기 때문에
     * 두 유스케이스가 공유하는 한 곳에 두는 편이 낫다 — 여기에도 두면 같은 규칙이 두 곳에 생긴다.
     * <p>
     * 한 평가사가 같은 날 맡는 건수에도 상한을 두지 않는다.
     *
     * @throws BusinessException 평가가 이미 끝난 신청이거나({@code NOT_ASSIGNABLE})
     *                           다른 평가사가 이미 배정된 경우({@code ALREADY_ASSIGNED})
     */
    public void assignTo(User evaluator) {
        // 상태를 먼저 본다. 이미 배정된 신청은 평가가 끝날 때까지 REQUESTED로 남아 이 관문을
        // 통과하고 아래에서 걸린다. 반대로 평가가 끝난 건은 배정도 되어 있지만, 그 경우의 원인은
        // "이미 배정됨"이 아니라 "배정 대상이 아님"이다 — 목록을 다시 봐도 돌아오지 않는 건이다
        if (status != EvaluationStatus.REQUESTED) {
            throw new BusinessException(EvaluationErrorCode.NOT_ASSIGNABLE);
        }
        if (this.evaluator != null) {
            throw new BusinessException(EvaluationErrorCode.ALREADY_ASSIGNED);
        }

        this.evaluator = evaluator;
    }
}
