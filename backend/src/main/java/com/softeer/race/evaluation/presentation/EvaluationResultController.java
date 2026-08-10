package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationResultService;
import com.softeer.race.evaluation.presentation.request.EvaluationRejectRequest;
import com.softeer.race.evaluation.presentation.request.EvaluationResultSubmitRequest;
import com.softeer.race.evaluation.presentation.response.EvaluationRejectionResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 평가사가 내는 방문 결과. 승인 제출과 반려가 <b>같은 유스케이스의 두 판정</b>이라 한 컨트롤러가
 * 받는다 — {@code EvaluationResultService}가 둘을 함께 맡는 것과 같은 이유다.
 * <p>
 * 클래스 매핑을 {@code /result}가 아니라 그 한 단계 위에 둔 것은 두 판정의 경로가 갈리기
 * 때문이다. {@code EvaluationAssignmentController}도 같은 모양이다.
 */
@RestController
@RequestMapping("/api/evaluations/{evaluationId}")
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
    @PutMapping("/result")
    public ResponseEntity<EvaluationResultResponse> submit(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId,
            @Valid @RequestBody EvaluationResultSubmitRequest request) {

        return ResponseEntity.ok(EvaluationResultResponse.from(evaluationResultService.submit(
                request.toCommand(evaluationId, authenticatedUser.id()))));
    }

    /**
     * 제출과 달리 PUT이 아니라 POST다. 결과 제출은 재제출을 교체로 처리해 몇 번을 보내도 같은
     * 상태에 수렴하지만, 반려는 되돌릴 수 없는 종료라 두 번째 요청이 409가 된다.
     * <p>
     * 201이 아닌 200인 것은 새로 조회할 수 있는 자원이 생기지 않아서다. 반려는 기존 신청의
     * 상태를 끝으로 옮기는 일이고, 그 결과는 신청 상세에서 본다.
     */
    @Override
    @PostMapping("/rejection")
    public ResponseEntity<EvaluationRejectionResponse> reject(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId,
            @Valid @RequestBody EvaluationRejectRequest request) {

        return ResponseEntity.ok(EvaluationRejectionResponse.from(evaluationResultService.reject(
                request.toCommand(evaluationId, authenticatedUser.id()))));
    }
}
