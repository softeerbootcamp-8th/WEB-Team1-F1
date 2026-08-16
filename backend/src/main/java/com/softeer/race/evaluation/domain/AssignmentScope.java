package com.softeer.race.evaluation.domain;

import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * 평가사의 "내 담당" 목록이 무엇을 담고 어떤 순서로 나갈지.
 * <p>
 * <b>담는 것과 순서를 한 자리에 둔다.</b> 둘이 같은 이유에서 나오기 때문이다 — {@link #ACTIVE}는
 * "언제 어디로 가야 하는가"를 묻는 목록이라 방문일이 임박한 순이고, {@link #COMPLETED}는 "방금
 * 무엇을 끝냈는가"를 묻는 목록이라 최근 처리 순이다. 상태 조건만 여기 두고 정렬을 쿼리에 박아
 * 두면 목록을 하나 더 열 때 그 이유가 두 곳으로 흩어진다.
 * <p>
 * 기본값은 컨트롤러가 {@code ACTIVE}로 잡는다. 완료된 진단이 기본 목록을 덮어 새로 나갈 건을
 * 가리는 것이 이 구분을 만든 이유다.
 * <p>
 * <b>"전체"에 해당하는 값을 두지 않는다.</b> 두 값의 합이 곧 전체라 세 번째 선택지는 같은 것을
 * 두 번 보여줄 뿐이고, 상태별 건수가 필요한 평가사 홈은 목록이 아니라 건수 조회
 * ({@code EvaluationRepository.countByEvaluatorIdGroupByStatus})를 쓴다.
 */
public enum AssignmentScope {

    /**
     * 아직 진단을 쓰지 않은 건들. 방문일이 임박한 순으로, 배정 대기 목록과 같은 기준이다.
     */
    ACTIVE(EvaluationStatus.diagnosisPending(), Sort.by(Sort.Direction.ASC, "visitDate", "id")),

    /**
     * 진단을 끝낸 건들. 최근 처리한 것이 위로 온다.
     * <p>
     * <b>{@code updatedAt}으로 정렬하는 근거.</b> 완료 시각을 따로 저장하지 않지만, 이 목록에
     * 들어오는 건에서 평가 행을 마지막으로 바꾼 사건은 승인 아니면 반려다 — 배정은 항상 그
     * 앞에 온다. 그래서 이 목록 안에서는 {@code updatedAt}이 곧 진단을 끝낸 시각이다.
     * ({@code EvaluationDetailInfo}가 제출 시각으로 차량의 {@code updatedAt}을 쓰는 것과 다르다.
     * 그쪽은 배정까지 섞이는 전체 구간에서 시각 하나를 정확히 짚어야 한다.)
     * <p>
     * 결과 재제출은 차량만 바꾸므로 순서가 움직이지 않는다. 이 목록이 세우는 것은 "진단을 끝낸
     * 순서"이지 "마지막으로 손댄 순서"가 아니다.
     * <p>
     * id를 둘째 키로 둔다. 같은 초에 두 건이 끝나면 정렬이 흔들려 새로고침마다 순서가 바뀐다.
     */
    COMPLETED(EvaluationStatus.diagnosisCompleted(), Sort.by(Sort.Direction.DESC, "updatedAt", "id"));

    private final Set<EvaluationStatus> statuses;
    private final Sort sort;

    AssignmentScope(Set<EvaluationStatus> statuses, Sort sort) {
        this.statuses = statuses;
        this.sort = sort;
    }

    public Set<EvaluationStatus> statuses() {
        return statuses;
    }

    public Sort sort() {
        return sort;
    }
}
