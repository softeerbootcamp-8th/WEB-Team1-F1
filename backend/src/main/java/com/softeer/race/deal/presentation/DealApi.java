package com.softeer.race.deal.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.deal.presentation.response.DealDetailResponse;
import com.softeer.race.deal.presentation.response.DealSliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Deal", description = "낙찰 이후 거래 API")
public interface DealApi {

    @Operation(summary = "내 거래 목록 조회",
            description = "내가 판매자이거나 구매자인 거래를 최근 것부터 10건씩 내려준다. 판매와 구매가 "
                    + "한 목록에 섞이며, mySide 로 어느 쪽인지 구분한다. 첫 요청은 cursor 없이 보내고 "
                    + "이후에는 직전 응답의 nextCursor 를 그대로 보낸다. 세션 쿠키가 필요하다.")
    ResponseEntity<DealSliceResponse> list(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "직전 응답의 nextCursor, 첫 요청에는 보내지 않는다", example = "7")
            Long cursor);

    @Operation(summary = "거래 상세 조회",
            description = "내가 당사자인 거래 하나를 내려준다. 없는 거래와 남의 거래는 모두 404 로 "
                    + "답하며 둘을 구분해 주지 않는다. 취소된 거래는 사유와 귀책이 함께 온다. "
                    + "세션 쿠키가 필요하다.")
    ResponseEntity<DealDetailResponse> detail(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "조회할 거래 식별자", example = "12")
            Long dealId);
}