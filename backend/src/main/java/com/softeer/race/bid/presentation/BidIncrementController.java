package com.softeer.race.bid.presentation;

import com.softeer.race.bid.application.BidIncrementService;
import com.softeer.race.bid.presentation.response.BidIncrementResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bid-increments")
public class BidIncrementController implements BidIncrementApi {

    private final BidIncrementService bidIncrementService;

    public BidIncrementController(BidIncrementService bidIncrementService) {
        this.bidIncrementService = bidIncrementService;
    }

    @Override
    @GetMapping
    public BidIncrementResponse getBidIncrements() {
        return BidIncrementResponse.from(bidIncrementService.policy());
    }
}
