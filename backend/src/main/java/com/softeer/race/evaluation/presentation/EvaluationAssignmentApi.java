package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationsResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationAssignmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "EvaluationAssignment", description = "평가사 배정 API")
public interface EvaluationAssignmentApi {

    @Operation(summary = "배정 대기 목록 조회",
            description = "아직 아무도 수락하지 않은 방문견적 신청을 방문일이 임박한 순서로 돌려줍니다. "
                    + "차량 제원과 방문 날짜 · 장소를 담아 평가사가 맡을지 판단할 수 있게 합니다. "
                    + "판매자 연락처는 담지 않습니다 — 이 목록은 평가사 전원이 보므로 배정받지 않을 "
                    + "사람들에게까지 전화번호가 뿌려집니다. 연락처는 수락에 성공한 뒤에 나갑니다. "
                    + "주행거리와 예상 시세도 비어 있습니다. 그 값을 채우는 것이 방문해서 할 일입니다. "
                    + "페이징은 없습니다 — 배정되는 즉시 빠지는 목록이라 규모의 상한이 낮습니다. "
                    + "세션 쿠키가 필요합니다. 역할은 확인하지 않아 로그인한 회원이면 누구나 볼 수 있습니다 "
                    + "— 인가 장치가 아직 없어 열어 둔 상태입니다.")
    ResponseEntity<AssignableEvaluationsResponse> findAssignable(AuthenticatedUser authenticatedUser);

    @Operation(summary = "방문견적 신청 수락",
            description = "신청 한 건을 수락해 요청자를 담당으로 확정합니다. "
                    + "먼저 수락한 한 명만 성립하고, 그 뒤의 수락은 409로 거부됩니다 — 목록을 보던 사이 "
                    + "다른 평가사가 먼저 수락하면 정상 흐름에서도 발생합니다. "
                    + "평가가 이미 끝난 신청도 409이며, 이 경우 목록을 다시 봐도 그 건은 돌아오지 않습니다. "
                    + "한 평가사가 맡는 건수에는 상한이 없습니다 — 같은 날짜의 여러 건도 수락할 수 있습니다. "
                    + "배정으로 신청 상태는 바뀌지 않습니다 — 평가가 끝날 때까지 REQUESTED입니다. "
                    + "방문 날짜와 장소는 받지 않습니다. 판매자가 정한 조건을 그대로 받아들이는 것이 수락입니다. "
                    + "확정되면 되돌릴 수 없습니다. 응답에는 방문 시 연락할 판매자 전화번호가 담깁니다. "
                    + "세션 쿠키가 필요합니다. 역할은 확인하지 않아 로그인한 회원이면 누구나 수락할 수 "
                    + "있습니다 — 인가 장치가 아직 없어 열어 둔 상태입니다.")
    ResponseEntity<EvaluationAssignmentResponse> assign(
            AuthenticatedUser authenticatedUser,
            @Parameter(description = "수락할 방문견적 신청 ID", example = "1") long evaluationId);
}
