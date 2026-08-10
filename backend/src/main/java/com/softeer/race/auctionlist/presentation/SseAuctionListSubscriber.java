package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseAuctionListSubscriber implements AuctionListSubscriber {

    private final SseEmitter emitter;

    // 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean open = true;

    // 전송 실패로 내려간 것과 이 연결을 끝낸 것은 다르다, 전자만 보고 끝내면 응답이 만료까지 남는다
    private final AtomicBoolean ended = new AtomicBoolean();

    SseAuctionListSubscriber(SseEmitter emitter) {
        this.emitter = emitter;
    }

    // 톰캣이 첫 쓰기까지 응답 헤더를 붙잡으므로, 아무것도 안 보내면 EventSource 가 onopen 을 못 쏜다
    // 주석 한 줄이라 데이터가 아니고, 첫 현황을 보내지 않는다는 결정과 어긋나지 않는다
    void flushHeaders() {
        try {
            emitter.send(SseEmitter.event().comment("open"));
        } catch (IOException | IllegalStateException e) {
            log.debug("목록 스트림을 여는 중 연결이 끊겼다", e);
            open = false;
        }
    }

    @Override
    public void close() {
        // 먼저 내린다, 끝내는 사이에 방송이 들어와도 완료된 응답에 쓰지 않는다
        open = false;

        // 쓰기에 실패해 이미 내려간 구독도 응답은 열려 있을 수 있어, 열림 여부로 건너뛰면 그것을 못 끝낸다
        // 두 번 끝내지 않기 위한 표시는 따로 둔다
        if (!ended.compareAndSet(false, true)) {
            return;
        }

        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // 컨테이너가 회수한 응답에서 무엇이 나올지 규약이 없다
            // 던지지 않는다고 계약에 적었으므로 여기서 막는다, 원하던 상태는 이미 되어 있다
            log.debug("목록 스트림 연결을 끝내는 중 이미 닫혀 있었다", e);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}
