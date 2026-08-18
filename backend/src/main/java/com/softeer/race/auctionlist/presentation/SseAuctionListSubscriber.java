package com.softeer.race.auctionlist.presentation;

import com.softeer.race.auctionlist.application.AuctionListSubscriber;
import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;
import com.softeer.race.auctionlist.presentation.response.AuctionCardResponse;
import com.softeer.race.auctionlist.presentation.response.AudienceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseAuctionListSubscriber implements AuctionListSubscriber {

    private final SseEmitter emitter;

    private final AuctionListMailbox mailbox = new AuctionListMailbox();

    // 느린 상대가 붙잡는 것은 여기 일꾼 하나뿐이다
    private final Executor workers;

    // 쓰기가 아직 가능한가, 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean alive = true;

    // 서버가 끝내기로 했다, 밀린 것을 다 내보낸 뒤에 실제로 끝난다
    private volatile boolean closing;

    // 전송에 실패해 내려간 구독도 응답은 열려 있을 수 있다, alive 만 보고 건너뛰면 그것을 못 끝낸다
    private final AtomicBoolean ended = new AtomicBoolean();

    SseAuctionListSubscriber(SseEmitter emitter, Executor workers) {
        this.emitter = emitter;
        this.workers = workers;
    }

    // 톰캣이 첫 쓰기까지 응답 헤더를 붙잡아, 아무것도 안 보내면 EventSource 가 onopen 을 못 쏜다
    // 연결 확인과 같은 주석 한 줄이라 같은 칸을 쓴다
    void flushHeaders() {
        ping();
    }

    @Override
    public void sendCard(AuctionCardInfo card) {
        offer(new CardMessage(card));
    }

    @Override
    public void sendAudience(long auctionId, int viewerCount) {
        offer(new AudienceMessage(auctionId, viewerCount));
    }

    @Override
    public void ping() {
        if (!isOpen()) {
            return;
        }

        mailbox.requestPing();
        startDrain();
    }

    // 곧바로 끝내지 않고 줄을 세운다, 마지막 카드를 내보낸 직후에 끝내면 그것이 사라진다
    @Override
    public void close() {
        closing = true;

        mailbox.requestClose();
        startDrain();
    }

    @Override
    public boolean isOpen() {
        return alive && !closing;
    }

    private void offer(AuctionListMessage message) {
        if (!isOpen()) {
            return;
        }

        mailbox.offer(message);
        startDrain();
    }

    // 이미 도는 일꾼이 있으면 맡기지 않는다, 그 일꾼이 새로 들어온 것까지 마저 가져간다
    private void startDrain() {
        if (!mailbox.claimDrain()) {
            return;
        }

        try {
            workers.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // 일꾼이 없어졌으면 부른 자리에서 마저 한다, 안 그러면 밀린 것과 끝내기가 영영 안 돈다
            log.debug("목록 배달 일꾼이 받지 않는다", e);
            drain();
        }
    }

    // 한 구독에 동시에 둘 돌지 않는다
    private void drain() {
        boolean more = true;

        while (more) {
            for (AuctionListMessage message : mailbox.drainMessages()) {
                write(message);
            }

            if (mailbox.drainPing()) {
                comment("keep-alive");
            }

            if (mailbox.drainClose()) {
                complete();
            }

            more = mailbox.renewDrain();
        }
    }

    // 화면이 이 이름으로 리스너를 나눠 단다, 실린 내용의 모양을 뜯어보고 분기하지 않는다
    // 봉인된 종류라 새 메시지가 생기면 컴파일러가 여기를 빠뜨리지 못하게 한다
    private void write(AuctionListMessage message) {
        switch (message) {
            case CardMessage(AuctionCardInfo card) -> send("card", AuctionCardResponse.from(card));
            case AudienceMessage audience ->
                    send("audience", new AudienceResponse(audience.auctionId(), audience.viewerCount()));
        }
    }

    private void complete() {
        alive = false;

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

    // 데이터가 아니라 SSE 주석 한 줄이다, 화면은 무시하고 우리는 연결이 살아 있는지만 확인한다
    private void comment(String text) {
        if (!alive) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().comment(text));
        } catch (IOException | IllegalStateException e) {
            // 직렬화할 값이 없어 서버 버그가 낄 자리가 없다, 찔러 보다 드러난 끊김이다
            log.debug("목록 스트림 연결 확인 실패", e);
            alive = false;
        }
    }

    // 끝내기가 줄을 선 뒤에도 밀린 카드는 나가야 한다, 막는 기준이 isOpen 이면 그것이 사라진다
    private void send(String eventName, Object data) {
        if (!alive) {
            return;
        }

        try {
            // 미디어 타입을 명시한다, 생략하면 어떤 변환기가 잡히는지가 데이터 타입에 딸려간다
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 탭을 닫거나 화면을 옮기면 나는 정상 경로다
            log.debug("목록 변화 전송 중 연결이 끊겼다, 이벤트 {}", eventName, e);
            alive = false;
        } catch (IllegalStateException e) {
            // 스프링이 IOException 을 뺀 전부를 이 타입으로 감싼다, 직렬화 실패가 여기로 와서 warn 이다
            log.warn("목록 변화 전송 실패, 이벤트 {}", eventName, e);
            alive = false;
        }
    }
}
