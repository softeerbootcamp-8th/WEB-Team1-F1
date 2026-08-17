package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.annotation.RequireRole;
import com.softeer.race.evaluation.application.EvaluationLookupService;
import com.softeer.race.evaluation.domain.AssignmentScope;
import com.softeer.race.evaluation.presentation.response.EvaluationAssignmentCountsResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationDetailResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationSummariesResponse;
import com.softeer.race.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목록 경로를 {@code /my} 하나로 합치지 않는다. 한 사람이 판매자이면서 평가사일 수 있어
 * 무엇을 돌려줄지 모호해지고, 그때 역할을 물어 갈라 주면 응답 형태가 요청자에 따라 달라진다.
 */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationLookupController implements EvaluationLookupApi {

    private final EvaluationLookupService evaluationLookupService;

    @Override
    @GetMapping("/my-requests")
    public ResponseEntity<EvaluationSummariesResponse> findMyRequests(
            @LoginUser AuthenticatedUser authenticatedUser) {

        return ResponseEntity.ok(EvaluationSummariesResponse.from(
                evaluationLookupService.findMyRequests(authenticatedUser.id())));
    }

    /**
     * 범위를 주지 않으면 진행 중이다. 기본값을 완료 쪽에 둘 이유가 없고, 값을 반드시 요구하면
     * 목록을 처음 여는 요청까지 무엇을 볼지 골라야 한다({@code AssignableEvaluationCursorRequest}의
     * sort와 같은 판단이다).
     */
    @Override
    @GetMapping("/my-assignments")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<EvaluationSummariesResponse> findMyAssignments(
            @LoginUser AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "ACTIVE") AssignmentScope scope) {

        return ResponseEntity.ok(EvaluationSummariesResponse.from(
                evaluationLookupService.findMyAssignments(authenticatedUser.id(), scope)));
    }

    /**
     * 목록과 나누지 않고 따로 둔다. 이 값을 읽는 평가사 홈은 카드가 아니라 수만 필요하고,
     * 목록이 범위로 갈린 뒤로는 어느 한쪽을 받아도 나머지를 셀 수 없다.
     */
    @Override
    @GetMapping("/my-assignments/count")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<EvaluationAssignmentCountsResponse> countMyAssignments(
            @LoginUser AuthenticatedUser authenticatedUser) {

        return ResponseEntity.ok(EvaluationAssignmentCountsResponse.from(
                evaluationLookupService.countMyAssignments(authenticatedUser.id())));
    }

    @Override
    @GetMapping("/{evaluationId}")
    public ResponseEntity<EvaluationDetailResponse> findDetail(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId) {

        return ResponseEntity.ok(EvaluationDetailResponse.from(
                evaluationLookupService.findDetail(evaluationId, authenticatedUser.id())));
    }
}
