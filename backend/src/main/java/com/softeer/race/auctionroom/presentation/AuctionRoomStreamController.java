package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.AuctionRoomStreamService;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionRoomStreamController implements AuctionRoomStreamApi {

    // 경매 하나의 수명보다 짧게 잡아 오래 붙어 있는 연결을 주기적으로 새로 세운다
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final AuctionRoomStreamService auctionRoomStreamService;

    @Override
    @GetMapping(path = "/{auctionId}/room/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable("auctionId") long auctionId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        RoomSubscriber subscriber = new SseRoomSubscriber(emitter);

        // 타임아웃 뒤에 완료 콜백이 잇달아 와서 해제가 두 번 불린다
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                // 콜백은 요청 스레드가 아니다, 주입받은 빈으로 불러야 트랜잭션 프록시를 탄다
                auctionRoomStreamService.unsubscribe(auctionId, subscriber);
            }
        };

        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(error -> release.run());

        auctionRoomStreamService.subscribe(auctionId, subscriber);

        return ResponseEntity.ok(emitter);
    }
}
