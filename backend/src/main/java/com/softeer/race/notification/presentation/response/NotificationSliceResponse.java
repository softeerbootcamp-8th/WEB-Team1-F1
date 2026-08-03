package com.softeer.race.notification.presentation.response;

import com.softeer.race.notification.application.dto.NotificationSliceInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "알림 목록 한 페이지")
public record NotificationSliceResponse(

        @Schema(description = "알림 목록, 최근 것부터")
        List<NotificationResponse> content,

        @Schema(description = "응답을 만든 서버 시각, 상대 시각 표시에 쓴다",
                example = "2026-08-03T12:00:00")
        LocalDateTime serverTime,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 요청에 그대로 보낼 커서, 마지막 페이지면 null", example = "33")
        Long nextCursor
) {

    public static NotificationSliceResponse from(NotificationSliceInfo info) {
        return new NotificationSliceResponse(
                info.content().stream().map(NotificationResponse::from).toList(),
                info.serverTime(),
                info.hasNext(),
                info.nextCursor());
    }
}