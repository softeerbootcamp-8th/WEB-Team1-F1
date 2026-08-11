package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.presentation.response.AuctionRoomResponse;
import com.softeer.race.auctionroom.presentation.response.RoomOpeningResponse;
import com.softeer.race.auctionroom.presentation.response.RoomResultResponse;
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
                    + "방이 열려 있는 동안에만 응답합니다. 개장 전과 종료 후에는 409 이고, code 로 어느 쪽인지 "
                    + "구분해 개장 안내 API나 결과 요약 API로 옮겨가면 됩니다. "
                    + "낙찰자 본인 여부와 내 입찰 표시가 세션 주인 기준으로 판정되므로 세션 쿠키가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "열려 있는 방의 현황입니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409",
            description = "아직 열리지 않았거나(ROOM_NOT_OPEN_YET) 이미 종료된(ROOM_ALREADY_CLOSED) 방입니다.")
    ResponseEntity<AuctionRoomResponse> enterRoom(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            // 아규먼트 리졸버가 쿠키에서 채우는 값이라 요청에 실리지 않는다, 감추지 않으면 springdoc 이
            // 이름 그대로 필수 쿼리 파라미터로 문서에 내보낸다
            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);

    @Operation(summary = "개장 전 경매방 안내",
            description = "아직 열리지 않은 경매방의 차량 요약, 시작가, 입장 가능 시각을 반환합니다. "
                    + "남은 시간은 서버가 세지 않습니다. 입장 가능 시각과 서버 시각의 차이로 화면이 셉니다. "
                    + "개장하면 이 API 는 409 가 되고 경매방 현황 조회로 옮겨가야 합니다. 모든 시각은 KST입니다.")
    @ApiResponse(responseCode = "200", description = "아직 열리지 않은 방의 안내입니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409",
            description = "이미 열린 방입니다(ROOM_ALREADY_OPEN). 경매방 현황 조회로 요청하세요.")
    ResponseEntity<RoomOpeningResponse> readOpening(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);

    @Operation(summary = "끝난 경매의 결과 요약",
            description = "차량 요약, 시작가와 최종 낙찰가, 낙찰자, 입찰 건수를 반환합니다. "
                    + "입찰이 한 건도 없었으면 유찰(UNSOLD)이고 낙찰가와 낙찰자가 없습니다. "
                    + "더 이상 바뀌지 않는 경매라 접속자 수와 호가창은 담지 않습니다. serverTime 은 결과값이 "
                    + "아니라 남은 열람 시간을 세는 기준이고 resultEndAt 과 짝입니다. "
                    + "판정 기준은 마감 시각이 아니라 확정된 경매 상태입니다. 마감 직후 확정되기 전까지는 409 이고, "
                    + "결과 확인 구간 5분이 지난 뒤에도 계속 조회됩니다. "
                    + "낙찰자 본인 여부가 세션 주인 기준으로 판정되므로 세션 쿠키가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "확정된 경매 결과입니다.")
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409",
            description = "아직 낙찰자가 확정되지 않은 경매입니다(AUCTION_NOT_ENDED).")
    ResponseEntity<RoomResultResponse> readResult(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
}