package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionRoom", description = "경매방 현황 조회 API")
public interface AuctionRoomApi {

    @Operation(summary = "경매방 현황 조회",
            description = "차량 요약, 현재가, 마감 시각, 최근 호가, 접속자 수를 반환합니다. "
                    + "호가마다 입찰자 역할과 조회한 사람의 입찰인지를 함께 내려줍니다. "
                    + "조회 자체가 접속 기록이 되므로 2초 주기로 호출합니다. 모든 시각은 KST입니다.")
    @ApiResponse(responseCode = "200", description = "방 단계와 무관하게 현황을 반환합니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    ResponseEntity<AuctionRoomResponse> enterRoom(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,
            @Parameter(name = "X-User-Id", description = "임시,인증 도입 시 제거",
                    in = ParameterIn.HEADER, required = true) long userId);
}