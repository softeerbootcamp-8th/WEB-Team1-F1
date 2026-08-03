package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.EvaluationImageService;
import com.softeer.race.evaluation.presentation.request.ImageUploadRequest;
import com.softeer.race.evaluation.presentation.response.ImageUploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/images")
@RequiredArgsConstructor
public class EvaluationImageController implements EvaluationImageApi {

    private final EvaluationImageService evaluationImageService;

    /**
     * 201이 아니라 200이다. 이 요청은 아무것도 만들지 않는다. 서명된 주소를 계산해 돌려줄 뿐이고,
     * 객체가 생기는 것은 클라이언트가 그 주소로 PUT 할 때다.
     * <p>
     * {@code authenticatedUser}를 쓰지 않지만 파라미터로 받는다. 인터셉터 등록만으로도 보호되기는
     * 하나, 이 저장소는 인증이 필요한 핸들러에 {@code @LoginUser}를 함께 두는 것을 규칙으로 삼는다.
     * 등록을 빠뜨렸을 때 조용히 열리는 대신 인자 리졸버가 401로 막아 준다.
     * <p>
     * TODO 역할 기반 인가가 들어오면 평가사(EVALUATOR)로 좁힌다. 지금은 로그인만 확인한다.
     */
    @Override
    @PostMapping("/presigned")
    public ResponseEntity<ImageUploadResponse> issue(
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody ImageUploadRequest request) {

        return ResponseEntity.ok(
                ImageUploadResponse.from(evaluationImageService.issue(request.toCommand())));
    }
}
