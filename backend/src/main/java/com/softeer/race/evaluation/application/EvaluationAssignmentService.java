package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.AssignableEvaluationCursor;
import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationInfo;
import com.softeer.race.evaluation.application.dto.info.AssignableEvaluationsInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationAssignmentInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 평가사 배정. 대기 중인 방문견적 신청을 보여주고, 먼저 수락한 한 명을 담당으로 확정한다.
 * <p>
 * 역할 기반 인가는 컨트롤러의 {@code RequireRole}과 인증 인터셉터가 공통으로 처리한다. 이 서비스는
 * 배정 대상 회원을 확인하고, 도메인이 자기 차량 수락과 배정 상태를 판정하도록 트랜잭션을 제공한다.
 * <p>
 * <b>배정하는 주체는 서버가 아니라 수락하는 사람이다.</b> 지역 · 부하로 서버가 골라 할당하는 방식을
 * 쓰지 않는다. 그러려면 평가사의 담당 지역과 가용 일정을 서버가 들고 있어야 하는데 그 정보가 없고,
 * 없는 상태로 자동 할당하면 갈 수 없는 곳에 배정된 뒤 사람이 되돌리는 일이 늘어난다.
 * <p>
 * 배정은 되돌릴 수 없다. 취소 · 재배정을 두지 않는 이유는 그 기능에 판매자 통보와 재공고 정책이
 * 함께 따라와야 하기 때문이다. 수락 전에 판단할 재료(방문 날짜 · 장소)는 목록이 이미 보여준다.
 * <p>
 * 한 사람이 맡을 수 있는 건수에 상한을 두지 않는다. 방문 시각을 모르는 상태에서 날짜 단위로 막으면
 * 실제로 겹치지 않는 일정까지 거부한다. 겹침을 정확히 막아야 한다면 방문 시각이 확정되는 다음
 * 단계에서 시간으로 판정한다.
 * <p>
 * 배정 사실을 판매자에게 알리지 않는다. 알림은 방문 일정이 확정되는 다음 단계에서 함께 붙인다 —
 * 지금 보내면 "평가사가 정해졌다"와 "언제 온다"가 따로 도착해, 판매자가 받은 첫 알림으로는
 * 아무것도 준비할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationAssignmentService {

    /**
     * 한 번에 내려보내는 신청 수.
     * <p>
     * 목록의 한 항목이 차량 제원과 방문 날짜 · 장소를 함께 보여주는 카드라 한 화면에 서너 건이
     * 들어간다. 20이면 평가사가 몇 번 스크롤할 분량이면서, 그 자리에서 수락되어 사라질 신청까지
     * 미리 읽어 두는 낭비가 크지 않다.
     */
    private static final int PAGE_SIZE = 20;

    private final EvaluationRepository evaluationRepository;
    private final UserRepository userRepository;

    /**
     * 아직 아무도 수락하지 않은 신청 한 페이지. 방문일이 임박한 순서다.
     * <p>
     * 요청자를 받지 않는다. 역할 판정은 핸들러 호출 전에 끝나고 목록 자체는 요청자에 따라 갈리지 않는다.
     * <p>
     * 커서가 없으면 첫 페이지다. 잘못된 커서를 첫 페이지로 되돌리는 판단은 여기서 하지 않는다 —
     * 요청이 성립하는지는 표현 계층이 검증한다.
     */
    public AssignableEvaluationsInfo findAssignable(AssignableEvaluationCursor cursor) {
        AssignableEvaluationCursor start = (cursor != null) ? cursor : AssignableEvaluationCursor.first();

        // 한 건을 더 읽어 다음 페이지가 있는지 본다. 건수를 세는 쿼리를 따로 내는 것보다 싸고,
        // 세는 시점과 읽는 시점이 갈려 "다음이 있다는데 열면 비어 있는" 상태가 되지 않는다
        List<AssignableEvaluationInfo> found =
                evaluationRepository.findAssignable(
                                EvaluationStatus.REQUESTED, start.visitDate(), start.evaluationId(),
                                Limit.of(PAGE_SIZE + 1))
                        .stream()
                        .map(AssignableEvaluationInfo::from)
                        .toList();

        boolean hasNext = found.size() > PAGE_SIZE;
        List<AssignableEvaluationInfo> page = hasNext ? found.subList(0, PAGE_SIZE) : found;

        return new AssignableEvaluationsInfo(page, hasNext, hasNext ? nextCursor(page.getLast()) : null);
    }

    /**
     * 배정 대기 중인 전체 건수. 평가사 홈이 목록 없이 이 값만 읽는다.
     */
    public long countAssignable() {
        return evaluationRepository.countAssignable(EvaluationStatus.REQUESTED);
    }

    // 마지막으로 준 항목이 다음 페이지가 이어 읽을 지점이다. 목록의 정렬과 같은 두 값을 담는다
    private static AssignableEvaluationCursor nextCursor(AssignableEvaluationInfo last) {
        return new AssignableEvaluationCursor(last.visitDate(), last.evaluationId());
    }

    /**
     * 신청 한 건을 수락해 자신을 담당으로 확정한다. 먼저 수락한 평가사만 성립한다.
     * <p>
     * 방문 날짜와 장소를 받지 않는다. 판매자가 정해 둔 값이고, 평가사가 하는 일은 그 조건을 그대로
     * 받아들이는 것이다. 받으면 평가사가 판매자와 합의 없이 일정을 바꿀 수 있다.
     */
    @Transactional
    public EvaluationAssignmentInfo assign(long evaluationId, long evaluatorId) {
        // 잠금 전에 조회한다. 계정이 없으면 성립할 수 없는 요청이라 잠금 대기열에 넣을 이유가 없다
        //
        // getReferenceById 가 아니라 findById 를 쓴다. 없는 계정이면 프록시 초기화 실패가
        // EntityNotFoundException 이 되어 최후방 핸들러의 500 이 되고, FK 위반으로 미뤄도 마찬가지다
        User evaluator = userRepository.findById(evaluatorId)
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.EVALUATOR_NOT_FOUND));

        // 여기부터 이 신청에 대한 수락이 한 번에 하나씩 처리된다
        Evaluation evaluation = evaluationRepository.findByIdForUpdate(evaluationId)
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        evaluation.assignTo(evaluator);

        // 저장 호출이 없다. 잠금과 함께 읽어 온 영속 엔티티라 커밋 시점에 변경이 반영된다
        return EvaluationAssignmentInfo.from(evaluation);
    }
}
