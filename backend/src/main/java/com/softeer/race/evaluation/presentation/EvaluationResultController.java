package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationResultService;
import com.softeer.race.evaluation.presentation.request.EvaluationResultSubmitRequest;
import com.softeer.race.evaluation.presentation.response.EvaluationResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/result")
@RequiredArgsConstructor
public class EvaluationResultController implements EvaluationResultApi {

    private final EvaluationResultService evaluationResultService;

    /**
     * PUT이고 201이 아니라 200이다. 재제출을 교체로 처리하므로 이 요청은 "이 평가의 결과는
     * 이것"이라는 대체이고, 같은 요청을 몇 번 보내도 결과가 같다.
     * <p>
     * 요청자를 본문이 아니라 세션에서 가져온다. 본문으로 받으면 남의 이름을 대고 제출할 수 있어,
     * 배정된 평가사만 제출한다는 규칙이 무의미해진다.
     */
    @Override
    @PutMapping
    public ResponseEntity<EvaluationResultResponse> submit(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId,
            @Valid @RequestBody EvaluationResultSubmitRequest request) {

        return ResponseEntity.ok(EvaluationResultResponse.from(evaluationResultService.submit(
                request.toCommand(evaluationId, authenticatedUser.id()))));
    }
}
