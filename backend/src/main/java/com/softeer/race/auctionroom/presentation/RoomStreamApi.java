package com.softeer.race.auctionroom.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import com.softeer.race.auth.domain.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AuctionRoom", description = "경매방 현황 조회 API")
public interface RoomStreamApi {

    @Operation(summary = "경매방 현황 실시간 구독",
            description = "연결을 열어 둔 채로 방 현황이 바뀔 때마다 현재 상태 전체를 내려줍니다. "
                    + "처음 연결하면 사람 수만 오고 첫 현황은 방 조회가 줍니다. 끊겼다 브라우저가 다시 붙으면 "
                    + "그 자리에서 마지막 현황을 한 번 받아 화면이 곧바로 실제와 맞습니다. "
                    + "이후에는 사람이 드나들거나 입찰이 성립할 때마다 옵니다. "
                    + "변경분이 아니라 매번 전체라 한 건을 놓쳐도 다음 전송이 덮습니다. "
                    + "보는 사람을 가리지 않아 내 입찰 표시도 낙찰자 본인 여부도 없습니다. 마스킹된 이름을 "
                    + "직접 비교하면 동명이인에서 틀리므로 그 둘은 조회 API로 받습니다. "
                    + "연결은 방이 열린 뒤 마감까지 열립니다. 입찰 시작 전 대기 구간에도 열려 사람이 모이는 것이 보입니다. "
                    + "마감되면 마지막 현황을 한 번 보내고 서버가 끊으므로, 다시 구독하지 말고 결과 요약 API로 갑니다. "
                    + "현황 조회는 마감 뒤에도 열려 있습니다. 모든 시각은 KST입니다. "
                    + "EventSource는 다른 출처에 자격 증명을 기본으로 보내지 않으므로 withCredentials를 켜야 "
                    + "세션 쿠키가 실립니다.")
    @ApiResponse(responseCode = "200", description = "현황 스트림을 엽니다.",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = RoomStateResponse.class)))
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409",
            description = "아직 열리지 않았거나(ROOM_NOT_OPEN_YET), 마감돼 현황 전송이 끝났거나"
                    + "(ROOM_STREAM_ENDED), 이미 종료된(ROOM_ALREADY_CLOSED) 방입니다. "
                    + "첫째는 기다렸다 다시 열면 되고 나머지 둘은 결과 요약 API로 가야 합니다.")
    ResponseEntity<SseEmitter> stream(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            // 브라우저가 스스로 붙이는 값이라 화면 코드가 넣을 것이 없다
            @Parameter(hidden = true) String lastEventId,

            // 값을 쓰지 않고 로그인 요구를 선언하는 파라미터다, 요청에 실리지 않으므로 문서에서 감춘다
            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
}
