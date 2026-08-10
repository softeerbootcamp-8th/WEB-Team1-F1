package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListStreamService;
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
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionListStreamController implements AuctionListStreamApi {

    // 오래 붙어 있는 연결을 주기적으로 새로 세운다, 경매방과 달리 경매 하나의 수명에 맞출 이유가 없다
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final AuctionListStreamService auctionListStreamService;

    // @LoginUser 파라미터를 두지 않는 것이 곧 비로그인 허용이다, AuthInterceptor 가 그 유무로만 인증을 요구한다
    @Override
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        SseAuctionListSubscriber subscriber = new SseAuctionListSubscriber(emitter);

        // 타임아웃 뒤에 완료 콜백이 잇달아 와서 해제가 두 번 불린다
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                // 콜백은 요청 스레드가 아니다, 주입받은 빈으로 불러야 프록시를 탄다
                auctionListStreamService.unsubscribe(subscriber);
            }
        };

        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(error -> {
            // 클라이언트 정상 종료도 여기로 오므로 경고면 소음이 된다, 진짜 문제도 조용히 사라지지 않게 흔적만 남긴다
            log.debug("목록 스트림 연결 오류", error);
            release.run();
        });

        auctionListStreamService.subscribe(subscriber);
        subscriber.flushHeaders();

        return ResponseEntity.ok(emitter);
    }
}