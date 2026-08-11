package com.softeer.race.deal.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.deal.application.DealProgressService;
import com.softeer.race.deal.application.DealQueryService;
import com.softeer.race.deal.presentation.request.DeliveryConfirmRequest;
import com.softeer.race.deal.presentation.request.TransportSubmitRequest;
import com.softeer.race.deal.presentation.response.DealDetailResponse;
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
