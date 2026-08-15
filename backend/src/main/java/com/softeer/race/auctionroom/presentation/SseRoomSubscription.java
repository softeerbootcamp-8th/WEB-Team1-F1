package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseRoomSubscription implements RoomSubscription {

    private final long auctionId;
    private final long viewerId;
    private final SseEmitter emitter;

    // 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean open = true;

    // 전송 실패로 내려간 것과 이 연결을 끝낸 것은 다르다, 전자만 보고 끝내면 응답이 만료까지 남는다
    private final AtomicBoolean completed = new AtomicBoolean();

    SseRoomSubscription(long auctionId, long viewerId, SseEmitter emitter) {
        this.auctionId = auctionId;
        this.viewerId = viewerId;
        this.emitter = emitter;
    }

    @Override
    public long viewerId() {
        return viewerId;
    }

    @Override
    public void send(RoomState state) {
        if (!open) {
            return;
        }

        try {
            emitter.send(RoomStateResponse.from(state));
        } catch (IOException e) {
            // 상대가 끊었다, 방이 닫히면 한꺼번에 몰리는 정상 경로다
            log.debug("경매방 현황 전송 중 연결이 끊겼다, 경매 {}", auctionId, e);
            open = false;
        } catch (IllegalStateException e) {
            // 스프링이 IOException 을 뺀 전부를 이 타입으로 감싼다, 이미 완료된 응답과 직렬화 실패가 여기로 온다
            // 후자면 방 전원에게 같은 예외가 찍혀 원인이 즉시 드러난다, 안 남기면 방이 통째로 조용히 끊긴다
            log.warn("경매방 현황 전송 실패, 경매 {}", auctionId, e);
            open = false;
        }
    }

    @Override
    public void ping() {
        if (!open) {
            return;
        }

        try {
            // 데이터가 아니라 SSE 주석 한 줄이다, 화면은 무시하고 우리는 연결이 살아 있는지만 확인한다
            emitter.send(SseEmitter.event().comment("keep-alive"));
        } catch (IOException | IllegalStateException e) {
            // 직렬화할 값이 없어 서버 버그가 낄 자리가 없다, 찔러 보다 드러난 끊김이다
            log.debug("경매방 연결 확인 실패, 경매 {}", auctionId, e);
            open = false;
        }
    }

    @Override
    public void close() {
        // 먼저 내린다, 끝내는 사이에 브로드캐스트가 들어와도 완료된 응답에 쓰지 않는다
        open = false;

        // 쓰기에 실패해 이미 내려간 구독도 응답은 열려 있을 수 있어, 열림 여부로 건너뛰면 그것을 못 끝낸다
        // 두 번 끝내지 않기 위한 표시는 따로 둔다
        if (!completed.compareAndSet(false, true)) {
            return;
        }

        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // send 와 달리 complete 는 예외를 한 타입으로 감싸 주지 않는다, 스프링이 안에서 감싸는 것은
            // 입출력 예외뿐이라 컨테이너가 회수한 응답에서 무엇이 나올지 규약이 없다
            // 던지지 않는다고 계약에 적었으므로 여기서 막는다, 원하던 상태는 이미 되어 있다
            log.debug("경매방 연결을 끝내는 중 이미 닫혀 있었다, 경매 {}", auctionId, e);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}
