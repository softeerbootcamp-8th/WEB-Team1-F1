package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.presentation.request.AuctionListCursorRequest;
import com.softeer.race.auctionlist.presentation.request.AuctionListFilterRequest;
import com.softeer.race.auctionlist.presentation.response.AuctionListResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionList", description = "경매글 목록 조회 API")
public interface AuctionListApi {

    @Operation(summary = "경매글 목록 조회",
            description = "진행중 → 예정 → 종료 순으로 묶고 그룹 안에서는 임박한 것부터 내려준다. "
                    + "첫 요청은 커서 없이 보내고, 이후에는 직전 응답의 nextCursor 쿼리 파라미터 형식으로 보낸다. "
                    + "조건은 manufacturer·transmission(단일), fuelTypes(다중), mileage·modelYear·price 의 Min/Max 로 걸고, "
                    + "조건을 바꾸면 커서 없이 첫 페이지부터 다시 요청해야 한다. "
                    + "남은 시간은 내려주지 않으므로 endAt 과 serverTime 의 차이로 계산합니다. 모든 시각은 KST 형식이다..")
    ResponseEntity<AuctionListResponse> list(AuctionListCursorRequest request, AuctionListFilterRequest filterRequest);

    @Operation(summary = "나의 경매 목록 조회",
            description = "일반 회원이나 딜러가 등록한 경매만 진행중 -> 예정 -> 종료 순으로 조회한다. 차량 조건 필터는 공개 목록과 같다.")
    @ApiResponse(responseCode = "403", description = "평가사 역할은 나의 경매 목록에 접근할 수 없습니다.")
    ResponseEntity<AuctionListResponse> listMine(AuthenticatedUser user, AuctionListCursorRequest request,
                                                 AuctionListFilterRequest filterRequest);
}
