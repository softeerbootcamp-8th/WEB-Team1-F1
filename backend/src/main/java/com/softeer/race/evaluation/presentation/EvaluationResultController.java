package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationResultService;
import com.softeer.race.evaluation.presentation.request.EvaluationResultPatchRequest;
import com.softeer.race.evaluation.presentation.request.EvaluationResultSubmitRequest;
import com.softeer.race.evaluation.presentation.response.EvaluationResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * PATCH이고 같은 경로다. 바꾸는 대상이 제출과 같은 "이 평가의 결과"이고 다른 것은 <b>보낸
     * 항목만 반영한다</b>는 점뿐이라, 리소스를 나누면 같은 것에 주소가 둘이 된다.
     * <p>
     * PUT과 달리 여러 번 보낸 결과가 한 번 보낸 것과 같다고 보장하지 않는다 — 이 요청은 "결과는
     * 이것"이 아니라 "이 항목을 이렇게"라서 이전 상태에 얹힌다. 사진 목록만은 대체라 예외다.
     */
    @Override
    @PatchMapping
    public ResponseEntity<EvaluationResultResponse> patch(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId,
            @Valid @RequestBody EvaluationResultPatchRequest request) {

        return ResponseEntity.ok(EvaluationResultResponse.from(evaluationResultService.patch(
                request.toCommand(evaluationId, authenticatedUser.id()))));
    }
}
