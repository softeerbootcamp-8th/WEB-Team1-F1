package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscriber;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
class SseRoomSubscriber implements RoomSubscriber {

    private final SseEmitter emitter;

    // 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean open = true;

    SseRoomSubscriber(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(RoomState state) {
        if (!open) {
            return;
        }

        try {
            emitter.send(RoomStateResponse.from(state));
        } catch (IOException | IllegalStateException e) {
            // 상대가 끊었거나 이미 완료된 응답이다, 직렬화 실패는 서버 버그라 잡지 않는다
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
            open = false;
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}
