package com.softeer.race.auction.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시작 알림 신청 여부
 * <p>
 * 값 하나뿐이지만 객체로 감싼다. 진위값을 본문에 그대로 내리면 나중에 필드가 하나 늘 때
 * 응답 형태가 통째로 바뀌어 화면이 같이 깨진다.
 */
public record AuctionStartAlertResponse(
        @Schema(description = "이 회원이 시작 알림을 신청했는지", example = "true")
        boolean subscribed
) {

    public static AuctionStartAlertResponse of(boolean subscribed) {
        return new AuctionStartAlertResponse(subscribed);
    }
}