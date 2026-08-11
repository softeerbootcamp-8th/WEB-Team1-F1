package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListService;
import com.softeer.race.auctionlist.presentation.request.AuctionListCursorRequest;
import com.softeer.race.auctionlist.presentation.request.AuctionListFilterRequest;
import com.softeer.race.auctionlist.presentation.response.AuctionListResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
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
    public ResponseEntity<AuctionListResponse> list(@Valid AuctionListCursorRequest request,
                                                    @Valid AuctionListFilterRequest filterRequest) {
        AuctionListResponse response = AuctionListResponse.from(
                auctionListService.list(request.toCursor(), request.filter(), filterRequest.toFilter()));

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<AuctionListResponse> listMine(@LoginUser AuthenticatedUser user,
                                                        @Valid AuctionListCursorRequest request,
                                                        @Valid AuctionListFilterRequest filterRequest) {
        AuctionListResponse response = AuctionListResponse.from(
                auctionListService.listMine(request.toCursor(), request.filter(), filterRequest.toFilter(), user.id()));

        return ResponseEntity.ok(response);
    }
}