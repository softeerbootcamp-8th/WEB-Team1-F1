package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.AssignableEvaluationCursorRequest;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationCountResponse;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationsResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationAssignmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
                    + "한 번에 20건까지 나가며, 이어서 보려면 직전 응답의 nextCursor를 그대로 돌려보냅니다. "
                    + "커서는 방문일과 신청 ID 두 값이고, 한쪽만 보내면 400입니다. "
                    + "커서 다음 자리부터 읽으므로 그사이 다른 평가사가 수락해 목록에서 빠진 신청이 있어도 "
                    + "남은 신청을 건너뛰지 않고, 같은 신청이 두 번 나오지도 않습니다. "
                    + "다만 이어 읽는 도중 접수된 신청이 이미 지나온 자리에 해당하면 그 회차에는 보이지 않고, "
                    + "목록을 다시 열면 나옵니다. "
                    + "전체 대기 건수는 이 응답에 없습니다 — 건수 조회를 따로 부르십시오. "
                    + "세션 쿠키와 평가사 역할이 필요합니다.")
    @ApiResponse(responseCode = "400", description = "커서를 한쪽만 보낸 경우입니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403",
            description = "평가사 역할이 아니거나 본인 차량의 신청인 경우입니다.")
    ResponseEntity<AssignableEvaluationsResponse> findAssignable(
            AssignableEvaluationCursorRequest request);

    @Operation(summary = "배정 대기 건수 조회",
            description = "아직 아무도 수락하지 않은 신청이 모두 몇 건인지 돌려줍니다. "
                    + "목록이 나누어 나가면서 필요해졌습니다 — 첫 페이지만 받아서는 전체 수를 알 수 없습니다. "
                    + "평가사 홈이 목록 없이 이 값만 읽습니다. "
                    + "수락 한 번에 값이 바뀌므로 캐시하지 않습니다. "
                    + "세션 쿠키와 평가사 역할이 필요합니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "평가사 역할이 아닌 경우입니다.")
    ResponseEntity<AssignableEvaluationCountResponse> countAssignable();

    @Operation(summary = "방문견적 신청 수락",
            description = "신청 한 건을 수락해 요청자를 담당으로 확정합니다. "
                    + "먼저 수락한 한 명만 성립하고, 그 뒤의 수락은 409로 거부됩니다 — 목록을 보던 사이 "
                    + "다른 평가사가 먼저 수락하면 정상 흐름에서도 발생합니다. "
                    + "평가가 이미 끝난 신청도 409이며, 이 경우 목록을 다시 봐도 그 건은 돌아오지 않습니다. "
                    + "한 평가사가 맡는 건수에는 상한이 없습니다 — 같은 날짜의 여러 건도 수락할 수 있습니다. "
                    + "배정으로 신청 상태는 바뀌지 않습니다 — 평가가 끝날 때까지 REQUESTED입니다. "
                    + "방문 날짜와 장소는 받지 않습니다. 판매자가 정한 조건을 그대로 받아들이는 것이 수락입니다. "
                    + "확정되면 되돌릴 수 없습니다. 응답에는 방문 시 연락할 판매자 전화번호가 담깁니다. "
                    + "세션 쿠키와 평가사 역할이 필요합니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "평가사 역할이 아닌 경우입니다.")
    ResponseEntity<EvaluationAssignmentResponse> assign(
            AuthenticatedUser authenticatedUser,
            @Parameter(description = "수락할 방문견적 신청 ID", example = "1") long evaluationId);
}
