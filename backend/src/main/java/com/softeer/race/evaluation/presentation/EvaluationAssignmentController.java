package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationAssignmentService;
import com.softeer.race.evaluation.presentation.response.AssignableEvaluationsResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationAssignmentResponse;
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
     * {@code authenticatedUser}를 서비스에 넘기지 않는다. 역할을 보지 않으므로 응답이 요청자에 따라
     * 갈리지 않는다. 그래도 파라미터를 남기는 이유는 이것이 인증을 요구하는 유일한 신호이기 때문이다 —
     * 지우면 인터셉터가 이 핸들러를 공개로 통과시킨다.
     */
    @Override
    @GetMapping("/assignable")
    public ResponseEntity<AssignableEvaluationsResponse> findAssignable(
            @LoginUser AuthenticatedUser authenticatedUser) {

        AssignableEvaluationsResponse response =
                AssignableEvaluationsResponse.from(evaluationAssignmentService.findAssignable());

        return ResponseEntity.ok(response);
    }

    /**
     * 201이다. 없던 배정이 이 요청으로 생긴다. Location은 붙이지 않는다 — 배정을 단건으로 조회할
     * 엔드포인트가 아직 없어 넣을 수 있는 주소가 전부 404를 가리킨다(VisitQuoteController와 같다).
     */
    @Override
    @PostMapping("/{evaluationId}/assignment")
    public ResponseEntity<EvaluationAssignmentResponse> assign(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId) {

        EvaluationAssignmentResponse response = EvaluationAssignmentResponse.from(
                evaluationAssignmentService.assign(evaluationId, authenticatedUser.id()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
