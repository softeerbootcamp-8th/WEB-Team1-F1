package com.softeer.race.bid.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.auth.presentation.annotation.RequireRole;
import com.softeer.race.bid.application.BidFacade;
import com.softeer.race.bid.application.dto.BidPlaceInfo;
import com.softeer.race.bid.presentation.request.BidPlaceRequest;
import com.softeer.race.bid.presentation.response.BidPlaceResponse;
import com.softeer.race.user.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 입찰 경로는 AuthWebMvcConfig 의 인터셉터 화이트리스트에 등록되어 있어야 한다
// 빠지면 @LoginUser 가 요청 속성을 못 찾아, 쿠키를 제대로 보내도 401이 된다
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class BidController implements BidApi {

    private final BidFacade bidFacade;

    // 201만 내리고 Location 은 붙이지 않는다, 입찰 단건 조회 엔드포인트가 없어서
    // 담을 수 있는 주소가 404가 된다. 이력 조회가 생기면 그때 created(uri) 로 바꾼다
    @Override
    @PostMapping("/{auctionId}/bids")
    @RequireRole({Role.GENERAL, Role.DEALER})
    public ResponseEntity<BidPlaceResponse> place(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser loginUser,
            @Valid @RequestBody BidPlaceRequest request) {

        BidPlaceInfo info = bidFacade.place(auctionId, loginUser.id(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(BidPlaceResponse.from(info));
    }
}
