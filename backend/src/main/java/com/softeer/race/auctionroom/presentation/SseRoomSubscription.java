package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomMessage;
import com.softeer.race.auctionroom.application.RoomState;
import com.softeer.race.auctionroom.application.RoomSubscription;
import com.softeer.race.auctionroom.application.ViewerCount;
import com.softeer.race.auctionroom.presentation.response.RoomStateResponse;
import com.softeer.race.auctionroom.presentation.response.ViewerCountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술만 안다
// 무엇을 언제 내보낼지는 사서함이 정하고 이 클래스는 실제로 쓰기만 한다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseRoomSubscription implements RoomSubscription {

    // 화면이 이 이름으로 리스너를 나눠 단다
    private static final String VIEWERS_EVENT = "viewers";

    private final long auctionId;
    private final long viewerId;
    private final SseEmitter emitter;

    private final RoomMailbox mailbox = new RoomMailbox();

    // 느린 상대가 붙잡는 것은 여기 일꾼 하나뿐이다
    private final Executor workers;

    // 쓰기가 아직 가능한가, 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean alive = true;

    // 서버가 끝내기로 했다, 밀린 것을 다 내보낸 뒤에 실제로 끝난다
    private volatile boolean closing;

    // 전송 실패로 내려간 것과 이 연결을 끝낸 것은 다르다, 전자만 보고 끝내면 응답이 만료까지 남는다
    private final AtomicBoolean completed = new AtomicBoolean();

    SseRoomSubscription(long auctionId, long viewerId, SseEmitter emitter, Executor workers) {
        this.auctionId = auctionId;
        this.viewerId = viewerId;
        this.emitter = emitter;
        this.workers = workers;
    }

    @Override
    public long viewerId() {
        return viewerId;
    }

    @Override
    public void send(RoomMessage message) {
        if (!isOpen()) {
            return;
        }

        mailbox.offer(message);
        startDrain();
    }

    @Override
    public void ping() {
        if (!isOpen()) {
            return;
        }

        mailbox.requestPing();
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
            log.debug("경매방 배달 일꾼이 받지 않는다, 경매 {}", auctionId, e);
            drain();
        }
    }

    // 한 구독에 동시에 둘 돌지 않는다, SseEmitter 에 두 스레드가 쓰지 않는 근거가 그것이다
    private void drain() {
        boolean more = true;

        while (more) {
            for (RoomMessage message : mailbox.drainMessages()) {
                write(message);
            }

            if (mailbox.drainPing()) {
                writePing();
            }

            if (mailbox.drainClose()) {
                complete();
            }

            more = mailbox.renewDrain();
        }
    }

    // 현황에는 이름을 붙이지 않는다, 붙이면 기본 이벤트로 받던 화면이 조용히 멈춘다
    // 봉인된 종류라 새 메시지가 생기면 컴파일러가 여기를 빠뜨리지 못하게 한다
    private void write(RoomMessage message) {
        if (!alive) {
            return;
        }

        try {
            switch (message) {
                case RoomState state -> emitter.send(RoomStateResponse.from(state));
                case ViewerCount viewers -> emitter.send(SseEmitter.event()
                        .name(VIEWERS_EVENT)
                        .data(ViewerCountResponse.from(viewers), MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            // 상대가 끊었다, 방이 닫히면 한꺼번에 몰리는 정상 경로다
            log.debug("경매방 현황 전송 중 연결이 끊겼다, 경매 {}", auctionId, e);
            alive = false;
        } catch (IllegalStateException e) {
            // 스프링이 IOException 을 뺀 전부를 이 타입으로 감싼다, 이미 완료된 응답과 직렬화 실패가 여기로 온다
            // 후자면 방 전원에게 같은 예외가 찍혀 원인이 즉시 드러난다, 안 남기면 방이 통째로 조용히 끊긴다
            log.warn("경매방 현황 전송 실패, 경매 {}", auctionId, e);
            alive = false;
        }
    }

    // 서버는 이 연결에 쓰기만 하고 읽지 않아서, 상대가 끊어도 다음 쓰기 전까지 모른다
    private void writePing() {
        if (!alive) {
            return;
        }

        try {
            // 데이터가 아니라 SSE 주석 한 줄이다, 화면은 무시하고 우리는 연결이 살아 있는지만 확인한다
            emitter.send(SseEmitter.event().comment("keep-alive"));
        } catch (IOException | IllegalStateException e) {
            // 직렬화할 값이 없어 서버 버그가 낄 자리가 없다, 찔러 보다 드러난 끊김이다
            log.debug("경매방 연결 확인 실패, 경매 {}", auctionId, e);
            alive = false;
        }
    }

    // 곧바로 끝내지 않고 줄을 세운다, 마감 현황을 내보낸 직후에 끊으므로 먼저 끝내면 그 현황이 사라진다
    @Override
    public void close() {
        closing = true;

        mailbox.requestClose();
        startDrain();
    }

    private void complete() {
        alive = false;

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
        return alive && !closing;
    }
}
