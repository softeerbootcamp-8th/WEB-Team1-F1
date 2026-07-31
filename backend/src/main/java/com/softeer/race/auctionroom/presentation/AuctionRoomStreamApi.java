package com.softeer.race.auctionroom.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AuctionRoom", description = "경매방 현황 조회 API")
public interface AuctionRoomStreamApi {

    @Operation(summary = "경매방 현황 실시간 구독",
            description = "연결을 열어 둔 채로 방 현황이 바뀔 때마다 현재 상태 전체를 내려줍니다. "
                    + "연결 직후 첫 현황이 한 번 오고, 이후에는 사람이 드나들거나 입찰이 성립할 때마다 옵니다. "
                    + "변경분이 아니라 매번 전체를 보내므로 한 건을 놓쳐도 다음 전송이 덮습니다. "
                    + "보는 사람을 가리지 않아 내 입찰 표시는 없습니다. 그 표시가 필요하면 조회 API를 씁니다. "
                    + "연결 자체가 접속자 한 명이므로 같은 사람이 창을 둘 열면 둘로 셉니다. 모든 시각은 KST입니다.")
    @ApiResponse(responseCode = "200", description = "현황 스트림을 엽니다.",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = RoomStateResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409", description = "아직 열리지 않았거나 이미 닫힌 방입니다.")
    ResponseEntity<SseEmitter> stream(@Parameter(description = "경매 식별자", example = "1") long auctionId);
}
