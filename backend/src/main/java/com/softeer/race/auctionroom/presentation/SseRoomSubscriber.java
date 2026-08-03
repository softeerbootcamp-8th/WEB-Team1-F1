package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseRoomSubscriber implements RoomSubscriber {

    private final long auctionId;
    private final SseEmitter emitter;

    // 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean open = true;

    SseRoomSubscriber(long auctionId, SseEmitter emitter) {
        this.auctionId = auctionId;
        this.emitter = emitter;
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
    public boolean isOpen() {
        return open;
    }
}
