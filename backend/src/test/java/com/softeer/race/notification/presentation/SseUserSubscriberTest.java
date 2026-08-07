package com.softeer.race.notification.presentation;

import com.softeer.race.notification.application.NotificationPush;
import com.softeer.race.notification.application.UserSubscriber;
import com.softeer.race.notification.domain.NotificationRow;
import com.softeer.race.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// 응답을 실제로 끝내는지만 본다, 무엇을 실어 보내는지는 채널 테스트가 맡는다
// 진짜 SseEmitter 는 초기화 전이면 전송이 실패하지 않아 끊긴 연결을 흉내낼 수 없어, 호출을 세는 것으로 바꿔 끼운다
class SseUserSubscriberTest {

    private static final long USER = 1L;

    @Test
    @DisplayName("두 번 끝내도 응답은 한 번만 끝난다")
    void closingTwiceEndsTheResponseOnce() {
        RecordingEmitter emitter = new RecordingEmitter();
        UserSubscriber subscriber = new SseUserSubscriber(USER, emitter);

        // 타임아웃 뒤에 완료 콜백이 잇달아 오는 것처럼 두 번 들어온다
        subscriber.close();
        subscriber.close();

        assertThat(emitter.completes).isEqualTo(1);
    }

    @Test
    @DisplayName("전송에 실패해 내려간 구독도 응답을 끝낸다")
    void brokenSubscriptionStillEndsTheResponse() {
        RecordingEmitter emitter = new BrokenEmitter();
        UserSubscriber subscriber = new SseUserSubscriber(USER, emitter);

        subscriber.send(push());

        // 이번 버그의 핵심이다. 살아 있지 않다고 해서 건너뛰면 응답이 만료까지 남는다
        assertThat(subscriber.isOpen()).isFalse();

        subscriber.close();

        assertThat(emitter.completes).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 회수된 응답을 끝내려 해도 예외가 밖으로 나가지 않는다")
    void closingRecycledResponseDoesNotThrow() {
        UserSubscriber subscriber = new SseUserSubscriber(USER, new RecycledEmitter());

        Throwable thrown = catchThrowable(subscriber::close);

        // 채널은 걷어낸 구독을 한 번에 끝낸다, 여기서 던지면 뒤의 구독이 안 닫힌다
        assertThat(thrown).isNull();
    }

    @Test
    @DisplayName("끝낸 뒤에는 더 보내지 않는다")
    void endedSubscriptionStopsSending() {
        RecordingEmitter emitter = new RecordingEmitter();
        UserSubscriber subscriber = new SseUserSubscriber(USER, emitter);

        subscriber.close();
        subscriber.send(push());
        subscriber.ping();

        assertThat(emitter.sends).isZero();
    }

    private static NotificationPush push() {
        return new NotificationPush(
                new NotificationRow(1L, NotificationType.AUCTION_WON, "낙찰되었습니다.", false, 7L,
                        LocalDateTime.of(2026, 8, 4, 12, 0)),
                1L);
    }

    // 응답을 끝내는 호출과 쓰기 호출을 센다
    private static class RecordingEmitter extends SseEmitter {

        private int completes;
        private int sends;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sends++;
        }

        @Override
        public void complete() {
            completes++;
        }
    }

    // 상대가 끊은 연결, 다음 쓰기에서야 드러난다
    private static final class BrokenEmitter extends RecordingEmitter {

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("상대가 끊었다");
        }
    }

    // 컨테이너가 이미 회수한 응답, complete 가 무엇을 던질지는 규약이 없다
    private static final class RecycledEmitter extends SseEmitter {

        @Override
        public void complete() {
            throw new IllegalStateException("이미 회수된 응답");
        }
    }
}
