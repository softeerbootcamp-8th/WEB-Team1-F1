package com.softeer.race.bid.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.bid.presentation.request.BidPlaceRequest;
import com.softeer.race.bid.presentation.response.BidPlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Bid", description = "입찰 API")
public interface BidApi {

    @Operation(summary = "입찰", description = """
            진행 중인 경매에 금액을 제시합니다. 로그인이 필요합니다.
            
            성립 여부는 저장된 경매 상태값이 아니라 서버 시각으로 판정합니다.
            화면이 진행 중으로 보여준 경매는 입찰이 되고, 개장 전·대기·마감 후에는 거절됩니다.
            
            금액은 첫 입찰이면 시작가 이상, 이후는 현재가에 최저 상승가를 더한 값 이상이면서
            그 상승가의 배수여야 합니다. 상승가 구간표는 GET /api/bid-increments 로 받습니다.
            
            마감 임박 입찰이면 마감이 연장되므로, 응답의 endAt 으로 카운트다운을 갱신해야 합니다.
            """)
    ResponseEntity<BidPlaceResponse> place(
            long auctionId, AuthenticatedUser loginUser, BidPlaceRequest request);
}
