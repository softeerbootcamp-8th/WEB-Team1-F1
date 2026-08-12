package com.softeer.race.storage.presentation;

import com.softeer.race.auth.presentation.annotation.RequireRole;
import com.softeer.race.storage.application.UploadService;
import com.softeer.race.storage.application.DealerLicenseUploadService;
import com.softeer.race.storage.presentation.request.DealerLicenseUploadRequest;
import com.softeer.race.storage.presentation.request.UploadRequest;
import com.softeer.race.storage.presentation.response.DealerLicenseUploadResponse;
import com.softeer.race.storage.presentation.response.UploadResponse;
import com.softeer.race.user.domain.Role;
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
    private final DealerLicenseUploadService dealerLicenseUploadService;

    /**
     * 201이 아니라 200이다. 이 요청은 아무것도 만들지 않는다. 서명된 주소를 계산해 돌려줄 뿐이고,
     * 객체가 생기는 것은 클라이언트가 그 주소로 PUT 할 때다.
     * <p>
     * 차량 평가 사진과 진단서 발급 경로이므로 평가사만 호출할 수 있다. 인증 주체 값은 서비스에서
     * 사용하지 않아 파라미터로 받지 않고, 메서드의 역할 애너테이션이 인증과 인가를 함께 요구한다.
     */
    @Override
    @PostMapping("/presigned")
    @RequireRole(Role.EVALUATOR)
    public ResponseEntity<UploadResponse> issue(
            @Valid @RequestBody UploadRequest request) {

        return ResponseEntity.ok(UploadResponse.from(uploadService.issue(request.toCommand())));
    }

    /** 회원가입 전 호출하므로 인증 주체를 요구하지 않는다. */
    @Override
    @PostMapping("/dealer-license/presigned")
    public ResponseEntity<DealerLicenseUploadResponse> issueDealerLicense(
            @Valid @RequestBody DealerLicenseUploadRequest request) {
        return ResponseEntity.ok(DealerLicenseUploadResponse.from(
                dealerLicenseUploadService.issue(request.contentType(), request.contentLength())));
    }
}
