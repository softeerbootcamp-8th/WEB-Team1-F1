package com.softeer.race.notification.presentation.response;

import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 한 건")
public record NotificationResponse(

        @Schema(description = "알림 식별자, 읽음 처리와 커서에 쓴다", example = "42")
        Long id,

        @Schema(description = "알림 종류", example = "AUCTION_WON")
        NotificationType type,

        @Schema(description = "표시 문구, 발행 당시 내용이 그대로 보관된다",
                example = "낙찰되었습니다. 거래를 진행해 주세요.")
        String message,

        @Schema(description = "읽음 여부", example = "false")
        boolean read,

        @Schema(description = "알림을 눌렀을 때 이동할 화면 주소", example = "/deals/7")
        String link,

        @Schema(description = "발행 시각", example = "2026-08-03T12:00:00")
        LocalDateTime createdAt
) {

    // referenceId 는 내려보내지 않는다. 클라이언트가 종류별로 주소를 조립하게 하면 알림 종류가 늘 때마다
    // 서버와 화면 두 곳을 고쳐야 하고, 한쪽만 고치면 조용히 어긋난다
    public static NotificationResponse from(NotificationRow row) {
        return new NotificationResponse(
                row.id(), row.type(), row.message(), row.read(), row.link(), row.createdAt());
    }
}