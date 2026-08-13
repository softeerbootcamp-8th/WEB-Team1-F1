package com.softeer.race.deal.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.deal.application.DealDocumentUploadService;
import com.softeer.race.deal.application.DealProgressService;
import com.softeer.race.deal.application.DealQueryService;
import com.softeer.race.deal.presentation.request.DealDocumentUploadRequest;
import com.softeer.race.deal.presentation.request.DeliveryConfirmRequest;
import com.softeer.race.deal.presentation.request.TransportSubmitRequest;
import com.softeer.race.deal.presentation.response.DealDetailResponse;
import com.softeer.race.deal.presentation.response.DealDocumentUploadResponse;
import com.softeer.race.deal.presentation.response.DealSliceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
public class DealController implements DealApi {

    private final DealQueryService dealQueryService;
    private final DealProgressService dealProgressService;
    private final DealDocumentUploadService dealDocumentUploadService;

    @Override
    @GetMapping
    public ResponseEntity<DealSliceResponse> list(
            @LoginUser AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Long cursor) {

        DealSliceResponse response = DealSliceResponse.from(
                dealQueryService.list(authenticatedUser.id(), cursor));

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{dealId}")
    public ResponseEntity<DealDetailResponse> detail(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId) {

        DealDetailResponse response = DealDetailResponse.from(
                dealQueryService.detail(authenticatedUser.id(), dealId));

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{dealId}/confirmation")
    public ResponseEntity<Void> confirmPurchase(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId) {

        dealProgressService.confirmPurchase(authenticatedUser.id(), dealId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 201 이 아니라 200 이다. 아무것도 만들지 않고 서명된 주소를 계산해 돌려줄 뿐이며, 객체는
     * 클라이언트가 그 주소로 PUT 할 때 생긴다.
     * <p>
     * 역할이 아니라 거래가 인가한다. 서류를 낼 자격은 "이 거래에서 지금 움직일 판매자인가"라
     * {@code @RequireRole} 로는 표현할 수 없다.
     */
    @Override
    @PostMapping("/{dealId}/documents/presigned")
    public ResponseEntity<DealDocumentUploadResponse> issueDocumentUpload(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId,
            @Valid @RequestBody DealDocumentUploadRequest request) {

        DealDocumentUploadResponse response = DealDocumentUploadResponse.from(
                dealDocumentUploadService.issue(authenticatedUser.id(), dealId,
                        request.contentType(), request.contentLength()));

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{dealId}/transport")
    public ResponseEntity<Void> submitTransport(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId,
            @Valid @RequestBody TransportSubmitRequest request) {

        dealProgressService.submitTransport(authenticatedUser.id(), dealId,
                request.documentUrl(), request.transportAt(), request.transportLocation());

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{dealId}/delivery")
    public ResponseEntity<Void> confirmDelivery(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId,
            @Valid @RequestBody DeliveryConfirmRequest request) {

        dealProgressService.confirmDelivery(authenticatedUser.id(), dealId,
                request.deliveryAt(), request.deliveryLocation());

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{dealId}/cancellation")
    public ResponseEntity<Void> cancel(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable Long dealId) {

        dealProgressService.cancel(authenticatedUser.id(), dealId);

        return ResponseEntity.noContent().build();
    }
}
