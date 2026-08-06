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
public interface AuctionRoomStreamApi {

    @Operation(summary = "경매방 현황 실시간 구독",
            description = "연결을 열어 둔 채로 방 현황이 바뀔 때마다 현재 상태 전체를 내려줍니다. "
                    + "연결 직후 첫 현황이 한 번 오고, 이후에는 사람이 드나들거나 입찰이 성립할 때마다 옵니다. "
                    + "변경분이 아니라 매번 전체를 보내므로 한 건을 놓쳐도 다음 전송이 덮습니다. "
                    + "보는 사람을 가리지 않아 내 입찰 표시는 없습니다. 그 표시가 필요하면 조회 API를 씁니다. "
                    + "낙찰이 확정되는 순간에도 낙찰자의 본인 여부는 오지 않습니다. 마스킹된 이름만 오므로 "
                    + "그것이 자신인지 알 수 없고, 자기 이름을 직접 마스킹해 비교하면 동명이인에서 틀립니다. "
                    + "낙찰자가 채워지는 순간 다시 조회하면 본인 여부가 담긴 응답을 받습니다. "
                    + "연결 자체가 접속자 한 명이므로 같은 사람이 창을 둘 열면 둘로 셉니다. 모든 시각은 KST입니다. "
                    + "로그인한 사람만 열 수 있습니다. 다만 방송 내용이 보는 사람과 무관해 신원은 쓰지 않고 "
                    + "로그인 여부만 확인합니다. EventSource는 다른 출처로 붙을 때 자격 증명을 기본으로 "
                    + "보내지 않으므로 withCredentials를 켜야 세션 쿠키가 실립니다.")
    @ApiResponse(responseCode = "200", description = "현황 스트림을 엽니다.",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = RoomStateResponse.class)))
    @ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404", description = "없는 경매이거나 경매글이 삭제된 경우입니다.")
    @ApiResponse(responseCode = "409",
            description = "아직 열리지 않았거나(ROOM_NOT_OPEN_YET) 이미 종료된(ROOM_ALREADY_CLOSED) 방입니다. "
                    + "앞쪽은 기다렸다 다시 열면 되고 뒤쪽은 결과 요약 API로 가야 합니다.")
    ResponseEntity<SseEmitter> stream(
            @Parameter(description = "경매 식별자", example = "1") long auctionId,

            // 값을 쓰지 않고 로그인 요구를 선언하는 파라미터다, 요청에 실리지 않으므로 문서에서 감춘다
            @Parameter(hidden = true) AuthenticatedUser authenticatedUser);
}
