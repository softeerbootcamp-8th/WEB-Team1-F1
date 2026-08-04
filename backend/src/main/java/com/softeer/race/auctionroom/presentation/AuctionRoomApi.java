package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "AuctionRoom", description = "경매방 현황 조회 API")
public interface AuctionRoomApi {

    @Operation(summary = "경매방 현황 조회",
            description = "차량 요약, 현재가, 마감 시각, 최근 호가, 접속자 수를 반환합니다. "
                    + "호가마다 입찰자 역할과 조회한 사람의 입찰인지를 함께 내려줍니다. "
                    + "방에 들어갈 때 화면을 그리는 용도입니다. 이후 갱신은 반복 조회가 아니라 구독 API로 받습니다. "
                    + "접속자 수는 조회가 아니라 열려 있는 구독 수로 셉니다. 모든 시각은 KST입니다. "
                    + "낙찰자 본인 여부와 내 입찰 표시가 세션 주인 기준으로 판정되므로 세션 쿠키가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "방 단계와 무관하게 현황을 반환합니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    ResponseEntity<AuctionRoomResponse> enterRoom(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            // 아규먼트 리졸버가 쿠키에서 채우는 값이라 요청에 실리지 않는다, 감추지 않으면 springdoc 이
            // 이름 그대로 필수 쿼리 파라미터로 문서에 내보낸다
            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
}