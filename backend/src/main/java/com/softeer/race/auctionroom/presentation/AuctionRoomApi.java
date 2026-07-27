package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionRoom", description = "경매방 현황 조회 API")
public interface AuctionRoomApi {

    @Operation(summary = "경매방 현황 조회",
            description = "현재가, 남은 시간, 최근 호가, 접속자 수를 반환합니다. "
                    + "조회 자체가 접속 기록이 되므로 2초 주기로 호출합니다. 모든 시각은 KST입니다.")
    ResponseEntity<AuctionRoomResponse> enterRoom(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,
            @Parameter(name = "X-User-Id", description = "임시,인증 도입 시 제거",
                    in = ParameterIn.HEADER, required = true) long userId);
}