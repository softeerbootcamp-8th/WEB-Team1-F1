package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.annotation.RequireRole;
import com.softeer.race.evaluation.application.EvaluationAssignmentService;
import com.softeer.race.evaluation.presentation.request.AssignableEvaluationCursorRequest;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationCountResponse;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationsResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationAssignmentResponse;
import com.softeer.race.user.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationAssignmentController implements EvaluationAssignmentApi {

    private final EvaluationAssignmentService evaluationAssignmentService;

    /**
     * 요청자 값은 서비스에 필요하지 않다. 메서드의 역할 애너테이션이 평가사 인증과 인가를 요구한다.
     */
    @Override
    @GetMapping("/assignable")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<AssignableEvaluationsResponse> findAssignable(
            @Valid AssignableEvaluationCursorRequest request) {

        AssignableEvaluationsResponse response = AssignableEvaluationsResponse.from(
                evaluationAssignmentService.findAssignable(request.toCursor(), request.sortOrDefault()));

        return ResponseEntity.ok(response);
    }

    /**
     * 목록과 나누지 않고 따로 둔다. 이 값을 읽는 평가사 홈은 카드가 아니라 수만 필요하다.
     */
    @Override
    @GetMapping("/assignable/count")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<AssignableEvaluationCountResponse> countAssignable() {

        AssignableEvaluationCountResponse response =
                AssignableEvaluationCountResponse.from(evaluationAssignmentService.countAssignable());

        return ResponseEntity.ok(response);
    }

    /**
     * 201이다. 없던 배정이 이 요청으로 생긴다. Location은 붙이지 않는다 — 배정을 단건으로 조회할
     * 엔드포인트가 아직 없어 넣을 수 있는 주소가 전부 404를 가리킨다(VisitQuoteController와 같다).
     */
    @Override
    @PostMapping("/{evaluationId}/assignment")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<EvaluationAssignmentResponse> assign(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId) {

        EvaluationAssignmentResponse response = EvaluationAssignmentResponse.from(
                evaluationAssignmentService.assign(evaluationId, authenticatedUser.id()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
