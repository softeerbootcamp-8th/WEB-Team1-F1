package com.softeer.race.bid.presentation;

import com.softeer.race.bid.application.BidIncrementService;
import com.softeer.race.bid.presentation.response.BidIncrementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bid-increments")
@RequiredArgsConstructor
public class BidIncrementController implements BidIncrementApi {

    private final BidIncrementService bidIncrementService;

    @Override
    @GetMapping
    public ResponseEntity<BidIncrementResponse> getBidIncrements() {
        BidIncrementResponse response = BidIncrementResponse.from(bidIncrementService.policy());
        return ResponseEntity.ok(response);
    }
}
