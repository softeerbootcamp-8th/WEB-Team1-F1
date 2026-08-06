package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationLookupService;
import com.softeer.race.evaluation.presentation.response.EvaluationDetailResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationSummariesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Override
    @GetMapping("/my-assignments")
    public ResponseEntity<EvaluationSummariesResponse> findMyAssignments(
            @LoginUser AuthenticatedUser authenticatedUser) {

        return ResponseEntity.ok(EvaluationSummariesResponse.from(
                evaluationLookupService.findMyAssignments(authenticatedUser.id())));
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
