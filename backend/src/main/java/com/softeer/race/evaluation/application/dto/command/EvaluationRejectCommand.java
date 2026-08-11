package com.softeer.race.evaluation.application.dto.command;

/**
 * 방문 결과 반려 유스케이스의 입력 전부. 행위 주체인 evaluatorId도 입력의 일부라 여기 담는다.
 * <p>
 * 값이 셋뿐인데도 Command로 묶는 이유는 {@link EvaluationResultSubmitCommand}와 같은 자리에 두기
 * 위해서다. 승인과 반려는 같은 유스케이스의 두 판정이라, 한쪽만 파라미터로 늘어놓으면 같은 서비스의
 * 두 메서드가 다른 규약을 갖는다.
 *
 * @param reason 판매자에게 전달할 반려 사유
 */
public record EvaluationRejectCommand(
        long evaluationId,
        long evaluatorId,
        String reason
) {
}
