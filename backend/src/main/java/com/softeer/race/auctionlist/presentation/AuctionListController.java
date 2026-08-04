package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListService;
import com.softeer.race.auctionlist.presentation.request.AuctionListCursorRequest;
import com.softeer.race.auctionlist.presentation.response.AuctionListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionListController implements AuctionListApi {

    private final AuctionListService auctionListService;

    @Override
    @GetMapping
    public ResponseEntity<AuctionListResponse> list(@Valid AuctionListCursorRequest request) {
        AuctionListResponse response =
                AuctionListResponse.from(auctionListService.list(request.toCursor(), request.filter()));

        return ResponseEntity.ok(response);
    }
}