package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomStreamService;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/auctions")
public class RoomStreamController implements RoomStreamApi {

    // 연결이 경매 하나를 끝까지 버티게 한다, 방 열림 30분에 연장까지 포함한 경매 최대 30분을 더한 값 위다
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(90).toMillis();

    // SSE 표준 헤더다, HttpHeaders 에 상수가 없어 직접 쓴다
    private static final String LAST_EVENT_ID = "Last-Event-ID";

    private final RoomStreamService roomStreamService;

    private final Executor roomBroadcastExecutor;

    // 롬복 생성자를 안 쓰는 것은 한정자 때문이다, 주기 작업 스케줄러 다섯도 Executor 라 타입만으로는 후보가 여섯이다
    public RoomStreamController(RoomStreamService roomStreamService,
                                @Qualifier("roomBroadcastExecutor") Executor roomBroadcastExecutor) {
        this.roomStreamService = roomStreamService;
        this.roomBroadcastExecutor = roomBroadcastExecutor;
    }

    @Override
    @GetMapping(path = "/{auctionId}/room/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @PathVariable("auctionId") long auctionId,

            // 브라우저가 스스로 다시 붙을 때만 실린다, 값은 우리가 내보낸 표시 그대로라 읽지 않고 유무만 본다
            @RequestHeader(value = LAST_EVENT_ID, required = false) String lastEventId,

            // AuthInterceptor 가 인증 요구를 이 파라미터의 유무로만 판정하므로 로그인을 요구하는
            // 유일한 선언 수단이다, 값을 안 쓰게 바뀌더라도 지우면 구독이 조용히 비로그인에 열린다
            @LoginUser AuthenticatedUser authenticatedUser) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        RoomSubscription subscription =
                new SseRoomSubscription(auctionId, authenticatedUser.id(), emitter, roomBroadcastExecutor);

        // 타임아웃 뒤에 완료 콜백이 잇달아 와서 해제가 두 번 불린다
        AtomicBoolean unsubscribed = new AtomicBoolean();
        Runnable unsubscribe = () -> {
            if (unsubscribed.compareAndSet(false, true)) {
                // 콜백은 요청 스레드가 아니다, 주입받은 빈으로 불러야 트랜잭션 프록시를 탄다
                roomStreamService.unsubscribe(auctionId, subscription);
            }
        };

        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(error -> {
            // 클라이언트 정상 종료도 여기로 오므로 경고면 소음이 된다, 진짜 문제도 조용히 사라지지 않게 흔적만 남긴다
            log.debug("경매방 현황 연결 오류, 경매 {}", auctionId, error);
            unsubscribe.run();
        });

        roomStreamService.subscribe(auctionId, subscription, lastEventId != null);

        return ResponseEntity.ok(emitter);
    }
}
