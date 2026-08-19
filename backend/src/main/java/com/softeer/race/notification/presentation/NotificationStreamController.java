package com.softeer.race.notification.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.notification.application.NotificationStreamService;
import com.softeer.race.notification.application.UserSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationStreamController implements NotificationStreamApi {

    // 스트림 타임아웃 시간을 세션 체감 유효 시간(15~30분)보다 짧게 10분으로 잡는다.
    // 연결이 열려 있는 동안에는 인터셉터를 다시 타지 않아 세션이 연장되지 않는다. 30분으로 두면 알림만
    // 열어 둔 회원은 재연결하는 순간 만료된 세션을 만나 401 이 된다. 10분이면 재연결이 세션을 연장한다.
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();

    private final NotificationStreamService notificationStreamService;

    // @LoginUser 를 Api 인터페이스가 아니라 이 구현체에 붙인다.
    // AuthInterceptor 가 이 애너테이션의 유무로 인증 요구를 판정하는데, 브리지된 구현 메서드만 보기
    // 때문에 인터페이스 쪽 선언은 보지 못한다. 빠지면 주체가 안 심겨 정상 로그인도 401 이 된다.
    @Override
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@LoginUser AuthenticatedUser authenticatedUser) {
        long userId = authenticatedUser.id();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        UserSubscriber subscriber = new SseUserSubscriber(userId, emitter);

        // 타임아웃 뒤에 완료 콜백이 잇달아 와서 해제가 두 번 불린다
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                // 콜백은 요청 스레드가 아니다, 주입받은 빈으로 불러야 프록시를 탄다
                notificationStreamService.unsubscribe(userId, subscriber);
            }
        };

        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(error -> {
            // 클라이언트 정상 종료도 여기로 오므로 경고면 소음이 된다, 진짜 문제도 조용히 사라지지 않게 흔적만 남긴다
            log.debug("알림 연결 오류, 회원 {}", userId, error);
            release.run();
        });

        notificationStreamService.subscribe(userId, subscriber);

        return ResponseEntity.ok(emitter);
    }
}