package com.softeer.race.evaluation.application.dto;

import java.time.LocalDate;

/**
 * 배정 대기 목록에서 직전 페이지가 끝난 지점.
 * <p>
 * 목록의 정렬이 {@code visitDate, id}라 커서도 두 값이다. visitDate만으로는 이어 읽을 수 없다 —
 * 날짜 단위라 같은 값이 페이지 크기를 넘길 만큼 몰리고, 그러면 그 날짜의 나머지를 통째로 건너뛰거나
 * 같은 자리를 다시 읽는다.
 * <p>
 * <b>AuctionListCursor와 달리 조회 시각(snapshotAt)을 담지 않는다.</b> 저쪽은 그룹을 가르는 기준이
 * 시간이라 페이지마다 다시 재면 항목이 그룹 사이를 옮겨 다니지만, 이 목록의 정렬 키인 방문일은
 * 시간이 흘러도 값이 변하지 않는다. 오히려 첫 페이지 시각으로 집합을 고정하면 그사이 접수된 신청이
 * 평가사에게 영영 보이지 않게 되어, 배정이 늦어질수록 손해가 커진다.
 * <p>
 * 커서보다 앞자리에 신청이 새로 꽂히면 이번에 이어 읽는 페이지에는 나오지 않는다. 목록을 다시 열면
 * 첫 페이지에 나오므로 사라지지는 않는다. 반대로 이미 읽은 자리는 값 비교로 넘어가므로 같은 신청이
 * 두 번 나오는 일은 없고, 커서가 가리키던 신청을 그사이 다른 평가사가 수락해 목록에서 빠져도
 * 커서는 값일 뿐 행이 아니라서 이어 읽을 지점이 흔들리지 않는다.
 */
public record AssignableEvaluationCursor(
        LocalDate visitDate,
        long evaluationId
) {

    /**
     * 첫 페이지의 시작점.
     * <p>
     * 커서가 없을 때를 {@code null} 분기로 두지 않는다. 조회 조건에 널 검사가 들어가면 인덱스를
     * 앞에서부터 타지 못하고, 첫 페이지와 이후 페이지가 서로 다른 쿼리가 되어 정렬 기준이 어긋날
     * 여지가 생긴다. 대신 어떤 신청보다도 앞서는 값을 넣어 조건 하나로 통일한다.
     * <p>
     * 방문일은 신청 시점에 과거일 수 없어({@code Evaluation.validateVisitDate}) EPOCH보다 앞선
     * 값이 저장되지 않는다. id는 IDENTITY라 1부터 시작해 0이 어떤 행보다도 앞선다.
     */
    public static AssignableEvaluationCursor first() {
        return new AssignableEvaluationCursor(LocalDate.EPOCH, 0L);
    }
}
