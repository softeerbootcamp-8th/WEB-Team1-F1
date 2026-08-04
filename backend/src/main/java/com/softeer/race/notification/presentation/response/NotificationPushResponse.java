package com.softeer.race.notification.presentation.response;

import com.softeer.race.notification.application.NotificationPush;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "새 알림 실시간 전달 내용")
public record NotificationPushResponse(

        @Schema(description = "새로 도착한 알림")
        NotificationResponse notification,

        @Schema(description = "이 알림을 포함한 안 읽은 건수, 헤더 벨의 숫자를 이 값으로 덮는다", example = "3")
        long unreadCount
) {

    // 알림 부분은 목록 응답과 같은 record 를 쓴다. 실시간으로 받은 알림과 목록으로 조회한 알림이
    // 화면에서 같은 모양이어야 하고, 필드가 갈리면 한쪽만 고쳐 어긋난다
    public static NotificationPushResponse from(NotificationPush push) {
        return new NotificationPushResponse(
                NotificationResponse.from(push.notification()), push.unreadCount());
    }
}