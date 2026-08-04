package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.AuctionRoomService;
import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionRoomController implements AuctionRoomApi {

    private final AuctionRoomService auctionRoomService;

    @Override
    @GetMapping("/{auctionId}/room")
    public ResponseEntity<AuctionRoomResponse> enterRoom(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser authenticatedUser) {

        AuctionRoomResponse response =
                AuctionRoomResponse.from(auctionRoomService.enterRoom(auctionId, authenticatedUser.id()));
        return ResponseEntity.ok(response);
    }
}