package com.softeer.race.sell.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.sell.application.SellService;
import com.softeer.race.sell.application.dto.info.SellApplicationInfo;
import com.softeer.race.sell.presentation.request.SellApplicationRequest;
import com.softeer.race.sell.presentation.response.SellApplicationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/sell")
@RequiredArgsConstructor
public class SellController implements SellApi {

    private final SellService sellService;

    /**
     * Location은 판매 신청이 아니라 생성된 경매를 가리킨다. 평가 요청을 만들지 않아 판매 신청 자체를
     * GET할 엔드포인트가 없으므로, 클라이언트가 실제로 따라갈 수 있는 유일한 리소스다.
     */
    @Override
    @PostMapping
    public ResponseEntity<SellApplicationResponse> apply(
            @LoginUser AuthenticatedUser authenticatedUser,
            @Valid @RequestBody SellApplicationRequest request) {

        SellApplicationInfo info = sellService.apply(request.toCommand(authenticatedUser.id()));
        SellApplicationResponse response = SellApplicationResponse.from(info);

        return ResponseEntity
                .created(URI.create("/api/auctions/" + response.auctionId()))
                .body(response);
    }
}
