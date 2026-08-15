package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomService;
import com.softeer.race.auctionroom.presentation.response.RoomResponse;
import com.softeer.race.auctionroom.presentation.response.RoomOpeningResponse;
import com.softeer.race.auctionroom.presentation.response.RoomResultResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class RoomController implements RoomApi {

    private final RoomService roomService;

    @Override
    @GetMapping("/{auctionId}/room")
    public ResponseEntity<RoomResponse> enterRoom(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser authenticatedUser) {

        RoomResponse response =
                RoomResponse.from(roomService.enterRoom(auctionId, authenticatedUser.id()));
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{auctionId}/room/opening")
    public ResponseEntity<RoomOpeningResponse> readOpening(
            @PathVariable("auctionId") long auctionId,

            // 안내에는 개인화가 없어 값을 쓰지 않는다, 그래도 받는 것은 이 선언이 인증을 거는 유일한
            // 신호이기 때문이다, 지우면 컴파일도 테스트도 통과한 채로 이 API 가 공개된다
            @LoginUser AuthenticatedUser authenticatedUser) {

        RoomOpeningResponse response = RoomOpeningResponse.from(roomService.readOpening(auctionId));
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{auctionId}/room/result")
    public ResponseEntity<RoomResultResponse> readResult(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser authenticatedUser) {

        RoomResultResponse response =
                RoomResultResponse.from(roomService.readResult(auctionId, authenticatedUser.id()));
        return ResponseEntity.ok(response);
    }
}