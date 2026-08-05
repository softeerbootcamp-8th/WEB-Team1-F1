package com.softeer.race.storage.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.storage.application.UploadService;
import com.softeer.race.storage.presentation.request.UploadRequest;
import com.softeer.race.storage.presentation.response.UploadResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로가 {@code /api/images}가 아니라 {@code /api/uploads}다. 이 엔드포인트는 이제 이미지뿐 아니라
 * 진단서 PDF도 발급하므로, images 아래에 두면 PDF를 이미지 경로로 요청하는 모양이 된다.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController implements UploadApi {

    private final UploadService uploadService;

    /**
     * 201이 아니라 200이다. 이 요청은 아무것도 만들지 않는다. 서명된 주소를 계산해 돌려줄 뿐이고,
     * 객체가 생기는 것은 클라이언트가 그 주소로 PUT 할 때다.
     * <p>
     * {@code authenticatedUser}를 쓰지 않지만 파라미터로 받는다. 인터셉터가 이 파라미터를 보고
     * 인증을 요구하므로, <b>지우면 인증이 함께 사라진다.</b>
     * <p>
     * TODO 역할 기반 인가가 들어오면 평가사(EVALUATOR)로 좁힌다. 지금은 로그인만 확인한다.
     */
    @Override
    @PostMapping("/presigned")
    public ResponseEntity<UploadResponse> issue(
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UploadRequest request) {

        return ResponseEntity.ok(UploadResponse.from(uploadService.issue(request.toCommand())));
    }
}
