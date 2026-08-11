package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.presentation.response.AuctionCardResponse;
import com.softeer.race.auctionlist.presentation.response.AudienceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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

    // 전송에 실패해 내려간 구독도 응답은 열려 있을 수 있다, open 만 보고 건너뛰면 그것을 못 끝낸다
    private final AtomicBoolean ended = new AtomicBoolean();

    SseAuctionListSubscriber(SseEmitter emitter) {
        this.emitter = emitter;
    }

    // 톰캣이 첫 쓰기까지 응답 헤더를 붙잡아, 아무것도 안 보내면 EventSource 가 onopen 을 못 쏜다
    void flushHeaders() {
        comment("open");
    }

    @Override
    public void ping() {
        comment("keep-alive");
    }

    // 화면이 이 이름으로 리스너를 나눠 단다, 실린 내용의 모양을 뜯어보고 분기하지 않는다
    @Override
    public void sendCard(AuctionCardInfo card) {
        send("card", AuctionCardResponse.from(card));
    }

    @Override
    public void sendAudience(long auctionId, int connectedCount) {
        send("audience", new AudienceResponse(auctionId, connectedCount));
    }

    @Override
    public void close() {
        // 먼저 내린다, 끝내는 사이에 방송이 들어와도 완료된 응답에 쓰지 않는다
        open = false;

        if (!ended.compareAndSet(false, true)) {
            return;
        }

        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // 컨테이너가 회수한 응답에서 무엇이 나올지 규약이 없다, 던지지 않는 것이 계약이라 여기서 막는다
            log.debug("목록 스트림 연결을 끝내는 중 이미 닫혀 있었다", e);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    // 데이터가 아니라 SSE 주석 한 줄이다, 화면은 무시하고 우리는 연결이 살아 있는지만 확인한다
    private void comment(String text) {
        if (!open) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().comment(text));
        } catch (IOException | IllegalStateException e) {
            // 직렬화할 값이 없어 서버 버그가 낄 자리가 없다, 찔러 보다 드러난 끊김이다
            log.debug("목록 스트림 연결 확인 실패", e);
            open = false;
        }
    }

    private void send(String eventName, Object data) {
        if (!open) {
            return;
        }

        try {
            // 미디어 타입을 명시한다, 생략하면 어떤 변환기가 잡히는지가 데이터 타입에 딸려간다
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 탭을 닫거나 화면을 옮기면 나는 정상 경로다
            log.debug("목록 변화 전송 중 연결이 끊겼다, 이벤트 {}", eventName, e);
            open = false;
        } catch (IllegalStateException e) {
            // 스프링이 IOException 을 뺀 전부를 이 타입으로 감싼다, 직렬화 실패가 여기로 와서 warn 이다
            log.warn("목록 변화 전송 실패, 이벤트 {}", eventName, e);
            open = false;
        }
    }
}
