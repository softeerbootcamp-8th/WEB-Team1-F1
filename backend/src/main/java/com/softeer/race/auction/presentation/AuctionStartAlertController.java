package com.softeer.race.auction.presentation;

import com.softeer.race.auction.application.AuctionStartAlertService;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.softeer.race.auction.presentation.response.AuctionStartAlertResponse;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionStartAlertController implements AuctionStartAlertApi {

    private final AuctionStartAlertService auctionStartAlertService;

    /**
     * POST 가 아니라 PUT 인 이유. 이 주소가 "이 경매에 대한 내 신청" 자원 하나를 특정하므로 서버가
     * 새 식별자를 발급할 컬렉션이 없고, 연산이 멱등이라 재전송이 안전하다는 계약을 메서드로 표현한다.
     */
    @Override
    @PutMapping("/{auctionId}/start-alert")
    public ResponseEntity<Void> subscribe(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser authenticatedUser) {

        boolean created = auctionStartAlertService.subscribe(auctionId, authenticatedUser.id());

        // 201 에 Location 을 싣지 않는다. 헤더가 없으면 요청 대상 URI 가 곧 생성된 자원인데(RFC 9110),
        // 여기서는 그게 정확하다 — 같은 주소로 신청 여부를 조회한다.
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).build()
                : ResponseEntity.noContent().build();
    }
    @Override
    @GetMapping("/{auctionId}/start-alert")
    public ResponseEntity<AuctionStartAlertResponse> readSubscription(
            @PathVariable("auctionId") long auctionId,
            @LoginUser AuthenticatedUser authenticatedUser) {

        boolean subscribed =
                auctionStartAlertService.isSubscribed(auctionId, authenticatedUser.id());

        return ResponseEntity.ok(AuctionStartAlertResponse.of(subscribed));
    }
}