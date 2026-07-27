package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.AuctionRoomService;
import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
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
            @RequestHeader("X-User-Id") long userId) {

        AuctionRoomResponse response = AuctionRoomResponse.from(auctionRoomService.enterRoom(auctionId, userId));
        return ResponseEntity.ok(response);
    }
}