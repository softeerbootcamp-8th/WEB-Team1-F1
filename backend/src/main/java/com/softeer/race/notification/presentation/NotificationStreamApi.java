package com.softeer.race.notification.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "회원 알림 API")
public interface NotificationStreamApi {

    @Operation(summary = "내 알림 실시간 구독",
            description = """
                    세션 쿠키로 본인 채널을 구독한다(EventSource, 다른 오리진이면 withCredentials 필요).
                    이벤트는 두 종류이며 이름으로 구분한다.

                    - `unread-count` : 연결 직후 한 번. 헤더 벨의 숫자를 이 값으로 맞춘다.
                    - `notification` : 새 알림이 저장될 때마다. 알림 한 건과 그 시점의 안 읽은 건수가 함께 온다.

                    연결이 끊긴 사이의 알림은 되짚어 보내지 않는다. 알림은 저장돼 있으므로 재연결 때 받는
                    `unread-count` 로 배지가 맞고, 내용은 목록 조회로 확인한다.

                    연결은 10분마다 서버가 끊고 브라우저가 자동으로 다시 붙는다. 세션 유효 시간보다 짧게
                    잡아 화면을 열어 둔 동안 로그인이 유지되게 한 것이다.
                    """)
    ResponseEntity<SseEmitter> stream(AuthenticatedUser authenticatedUser);
}