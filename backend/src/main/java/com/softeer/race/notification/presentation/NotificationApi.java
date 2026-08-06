package com.softeer.race.notification.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.notification.presentation.response.NotificationSliceResponse;
import com.softeer.race.notification.presentation.response.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Notification", description = "회원 알림 API")
public interface NotificationApi {

    @Operation(summary = "내 알림 목록 조회",
            description = "최근 것부터 10건씩 내려준다. 첫 요청은 cursor 없이 보내고, 이후에는 직전 응답의 "
                    + "nextCursor 를 그대로 보낸다. 읽는 도중 새 알림이 쌓여도 같은 알림이 두 번 나오거나 "
                    + "빠지지 않는다. 상대 시각(\"3분 전\")은 createdAt 과 serverTime 의 차이로 계산한다. "
                    + "세션 쿠키가 필요하다.")
    ResponseEntity<NotificationSliceResponse> list(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "직전 응답의 nextCursor, 첫 요청에는 보내지 않는다", example = "33")
            Long cursor);

    @Operation(summary = "안 읽은 알림 건수",
            description = "헤더 벨의 숫자에 쓴다. 목록을 열지 않은 상태에서도 필요하므로 목록과 분리했다. "
                    + "세션 쿠키가 필요하다.")
    ResponseEntity<UnreadCountResponse> countUnread(AuthenticatedUser authenticatedUser);

    @Operation(summary = "알림 한 건 읽음 처리",
            description = "이미 읽은 알림에 다시 요청해도 성공한다. 없는 알림과 남의 알림은 모두 404 로 "
                    + "답하며 둘을 구분해 주지 않는다. 세션 쿠키가 필요하다.")
    ResponseEntity<Void> markRead(
            AuthenticatedUser authenticatedUser,

            @Parameter(description = "읽음으로 바꿀 알림 식별자", example = "42")
            Long notificationId);

    @Operation(summary = "내 알림 전체 읽음 처리",
            description = "안 읽은 알림만 대상으로 한다. 읽을 알림이 없어도 성공한다. 세션 쿠키가 필요하다.")
    ResponseEntity<Void> markAllRead(AuthenticatedUser authenticatedUser);
}