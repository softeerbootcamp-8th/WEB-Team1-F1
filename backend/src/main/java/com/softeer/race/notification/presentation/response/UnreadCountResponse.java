package com.softeer.race.notification.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "안 읽은 알림 건수")
public record UnreadCountResponse(

        @Schema(description = "안 읽은 알림 건수", example = "3")
        long unreadCount
) {
}