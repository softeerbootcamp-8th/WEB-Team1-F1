package com.softeer.race.notification.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 알림 SSE 전송의 실제 소켓 쓰기와 열린 연결을 측정한다.
 *
 * <p>업무 이벤트 수가 아니라 구독 단위다. 회원 한 명이 탭 두 개를 열면 알림 한 건을 두 번 쓰고,
 * 구독이 없으면 DB 알림은 저장돼도 쓰기는 0번이다. 이 경계를 지켜야 저장 비용과 전달 비용을
 * 분리할 수 있다.</p>
 */
@Component
public class NotificationDeliveryMetrics {

    static final String CONNECTIONS = "notification.sse.connections";
    static final String SENDS = "notification.sse.sends";
    static final String SEND_DURATION = "notification.sse.send.duration";

    private static final String ATTEMPT = "attempt";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger connections = new AtomicInteger();
    private final Map<Event, SendMeters> sendMeters = new EnumMap<>(Event.class);

    public NotificationDeliveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder(CONNECTIONS, connections, AtomicInteger::get)
                .description("현재 열려 있는 알림 SSE 연결 수")
                .register(meterRegistry);

        for (Event event : Event.values()) {
            sendMeters.put(event, new SendMeters(
                    counter(event, ATTEMPT),
                    counter(event, SUCCESS),
                    counter(event, FAILURE),
                    Timer.builder(SEND_DURATION)
                            .description("알림 SSE 구독 한 개에 쓰는 시간")
                            .tag("event", event.tag)
                            .publishPercentiles(0.5, 0.95, 0.99)
                            .register(meterRegistry)));
        }
    }

    public void connectionOpened() {
        connections.incrementAndGet();
    }

    public void connectionsClosed(int count) {
        connections.addAndGet(-count);
    }

    /**
     * 구독 한 개에 실제로 쓰는 호출을 기록한다.
     *
     * <p>{@code action}이 예외를 던지거나, 호출 뒤 구독이 닫힌 상태가 되면 실패다. 현재 SSE 구현은
     * 네트워크 예외를 삼키고 자신을 닫기 때문에 두 신호를 모두 봐야 실패를 놓치지 않는다.</p>
     */
    public void recordSend(Event event, Runnable action, BooleanSupplier isOpen) {
        SendMeters meters = sendMeters.get(event);
        meters.attempt.increment();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            action.run();

            if (isOpen.getAsBoolean()) {
                meters.success.increment();
            } else {
                meters.failure.increment();
            }
        } catch (RuntimeException e) {
            meters.failure.increment();
            throw e;
        } finally {
            sample.stop(meters.duration);
        }
    }

    private Counter counter(Event event, String outcome) {
        return Counter.builder(SENDS)
                .description("알림 SSE 전송 결과별 횟수")
                .tag("event", event.tag)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    public enum Event {
        NOTIFICATION("notification"),
        UNREAD_COUNT("unread-count"),
        HEARTBEAT("heartbeat");

        private final String tag;

        Event(String tag) {
            this.tag = tag;
        }
    }

    private record SendMeters(Counter attempt, Counter success, Counter failure, Timer duration) {
    }
}
