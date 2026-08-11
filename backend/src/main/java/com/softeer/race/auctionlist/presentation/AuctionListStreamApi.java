package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.presentation.response.AuctionCardResponse;
import com.softeer.race.auctionlist.presentation.response.AudienceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AuctionList", description = "경매글 목록 조회 API")
public interface AuctionListStreamApi {

    @Operation(summary = "경매 목록 변화 실시간 구독",
            description = "연결을 열어 둔 채로 목록에 보일 값이 바뀔 때마다 내려줍니다. "
                    + "화면이 시계로 셀 수 있는 것(단계 전이, 남은 시간, 카드의 자리)은 보내지 않습니다. "
                    + "이벤트 이름으로 둘이 갈립니다. card 는 목록 조회의 카드 한 장과 같은 모양이고 "
                    + "입찰과 경매 시작·마감 때 옵니다. audience 는 경매 식별자와 시청자 수 둘뿐이고 "
                    + "수가 바뀐 경매만 옵니다. connectedCount 는 두 이벤트에 모두 실리며 "
                    + "나중에 온 값이 최신입니다. "
                    + "연결 직후에는 아무 이벤트도 오지 않습니다. 서버는 보고 있는 페이지를 모르므로 "
                    + "첫 목록은 조회 API로 받고, 연결이 끊겼다 다시 붙었으면 목록을 다시 읽어야 합니다. "
                    + "card 는 변경분이 아니라 카드 전체라 한 건을 놓쳐도 다음 전송이 덮습니다. "
                    + "로그인은 필요하지 않습니다. 목록 조회가 비로그인이라 같은 데이터를 미는 통로도 같습니다. "
                    + "모든 시각은 KST입니다.")
    @ApiResponse(responseCode = "200",
            description = "변화 스트림을 엽니다. 이벤트 이름이 card 인지 audience 인지에 따라 본문이 갈립니다.",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(oneOf = {AuctionCardResponse.class, AudienceResponse.class})))
    ResponseEntity<SseEmitter> stream();
}