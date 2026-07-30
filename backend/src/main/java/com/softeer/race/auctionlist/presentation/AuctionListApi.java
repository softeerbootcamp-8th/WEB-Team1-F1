package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.presentation.request.AuctionListCursorRequest;
import com.softeer.race.auctionlist.presentation.response.AuctionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionList", description = "경매글 목록 조회 API")
public interface AuctionListApi {

    @Operation(summary = "경매글 목록 조회",
            description = "진행중 → 예정 → 종료 순으로 묶고 그룹 안에서는 임박한 것부터 내려준다. "
                    + "첫 요청은 커서 없이 보내고, 이후에는 직전 응답의 nextCursor 쿼리 파라미터 형식으로 보낸다. "
                    + "남은 시간은 내려주지 않으므로 endAt 과 serverTime 의 차이로 계산합니다. 모든 시각은 KST 형식이다..")
    ResponseEntity<AuctionListResponse> list(AuctionListCursorRequest request);
}