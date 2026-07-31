package com.softeer.race.bid.presentation;

import com.softeer.race.bid.presentation.response.BidIncrementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "BidIncrement", description = "최저 입찰 상승가 기준 API")
public interface BidIncrementApi {

    @Operation(summary = "최저 입찰 상승가 구간표 조회",
            description = "가격대별 최저 상승가 구간표를 하한 오름차순으로 반환합니다. "
                    + "클라이언트는 이 표로 다음 입찰 금액을 미리 계산할 수 있습니다.")
    ResponseEntity<BidIncrementResponse> getBidIncrements();
}
