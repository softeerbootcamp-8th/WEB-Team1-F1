package com.softeer.race.auction.presentation;

import com.softeer.race.auction.application.AuctionService;
import com.softeer.race.auction.application.dto.AuctionCreateInfo;
import com.softeer.race.auction.application.dto.AuctionUpdateInfo;
import com.softeer.race.auction.presentation.request.AuctionCreateRequest;
import com.softeer.race.auction.presentation.request.AuctionUpdateRequest;
import com.softeer.race.auction.presentation.response.AuctionCreateResponse;
import com.softeer.race.auction.presentation.response.AuctionUpdateResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Tag(name = "Auction", description = "경매 API")
public class AuctionController {

    private final AuctionService auctionService;

    @Operation(summary = "경매글 등록", description = "보유 차량으로 경매글을 등록하고 경매를 예약합니다.")
    @PostMapping
    public ResponseEntity<AuctionCreateResponse> create(@Valid @RequestBody AuctionCreateRequest request) {
        AuctionCreateInfo info = auctionService.create(request.vehicleId(), request.startPrice(), request.startAt());
        AuctionCreateResponse response = AuctionCreateResponse.from(info);

        return ResponseEntity
                .created(URI.create("/api/auctions/" + response.auctionId()))
                .body(response);
    }

    @PatchMapping("/{auctionId}")
    public ResponseEntity<AuctionUpdateResponse> update(@LoginUser AuthenticatedUser authenticatedUser,
                                                        @PathVariable long auctionId,
                                                        @Valid @RequestBody AuctionUpdateRequest request) {
        AuctionUpdateInfo info = auctionService.update(authenticatedUser.id(), auctionId, request.startPrice(), request.startAt());

        return ResponseEntity.ok(AuctionUpdateResponse.from(info));
    }

    @DeleteMapping("/{auctionId}")
    public ResponseEntity<Void> delete(@LoginUser AuthenticatedUser authenticatedUser,
                                       @PathVariable long auctionId) {
        auctionService.delete(authenticatedUser.id(), auctionId);
        return ResponseEntity.noContent().build();
    }

}
