package com.softeer.race.notification.presentation;

import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.UserSubscriber;
import com.softeer.race.notification.presentation.response.NotificationPushResponse;
import com.softeer.race.notification.presentation.response.UnreadCountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.IOException;

// 열린 SSE 응답을 채널이 아는 모양으로 감싼다, 전송 기술을 아는 곳은 여기까지다
// equals 를 정의하지 않는다, 연결 하나에 객체 하나라 객체 자체가 식별자다
@Slf4j
class SseUserSubscriber implements UserSubscriber {

    // 이벤트에 이름을 붙인다. 화면이 이 이름으로 리스너를 나눠 달아서, 실린 내용의 모양을 뜯어보고
    // 분기하지 않는다. 경매방은 보낼 것이 한 종류라 이름이 없지만 여기는 둘이고 거래 갱신이 더 얹힌다.
    private static final String NOTIFICATION = "notification";
    private static final String UNREAD_COUNT = "unread-count";

    private final long userId;
    private final SseEmitter emitter;

    // 보내는 스레드와 살아 있는지 묻는 스레드가 다르다
    private volatile boolean open = true;

    // 전송 실패로 내려간 것과 이 연결을 끝낸 것은 다르다, 전자만 보고 끝내면 응답이 만료까지 남는다
    private final AtomicBoolean ended = new AtomicBoolean();

    SseUserSubscriber(long userId, SseEmitter emitter) {
        this.userId = userId;
        this.emitter = emitter;
    }

    @Override
    public void send(NotificationPush push) {
        send(NOTIFICATION, NotificationPushResponse.from(push));
    }

    @Override
    public void sendUnreadCount(long unreadCount) {
        send(UNREAD_COUNT, new UnreadCountResponse(unreadCount));
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
            log.debug("알림 연결 확인 실패, 회원 {}", userId, e);
            open = false;
        }
    }

    @Override
    public void close() {
        // 먼저 내린다, 끝내는 사이에 알림이 들어와도 완료된 응답에 쓰지 않는다
        open = false;

        // 쓰기에 실패해 이미 내려간 구독도 응답은 열려 있을 수 있어, 열림 여부로 건너뛰면 그것을 못 끝낸다
        // 두 번 끝내지 않기 위한 표시는 따로 둔다
        if (!ended.compareAndSet(false, true)) {
            return;
        }

        try {
            emitter.complete();
        } catch (RuntimeException e) {
            // send 와 달리 complete 는 예외를 한 타입으로 감싸 주지 않는다, 컨테이너가 회수한 응답에서
            // 무엇이 나올지 규약이 없다. 던지지 않는다고 계약에 적었으므로 여기서 막는다
            log.debug("알림 연결을 끝내는 중 이미 닫혀 있었다, 회원 {}", userId, e);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    // 미디어 타입을 명시한다, 생략하면 어떤 변환기가 잡히는지가 데이터 타입에 딸려가서 계약이 흐려진다
    private void send(String eventName, Object data) {
        if (!open) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 상대가 끊었다. 탭을 닫거나 화면을 옮기면 나는 정상 경로라 debug 로만 남긴다
            log.debug("알림 전송 중 연결이 끊겼다, 회원 {}", userId, e);
            open = false;
        } catch (IllegalStateException e) {
            // 스프링이 IOException 을 뺀 전부를 이 타입으로 감싼다 — 완료된 응답과 직렬화 실패가 같이 온다.
            // 직렬화 실패는 서버 버그라 warn 이어야 한다. 위와 한 catch 로 묶으면 알림이 조용히 멈춘다.
            log.warn("알림 전송 실패, 회원 {} 이벤트 {}", userId, eventName, e);
            open = false;
        }
    }
}