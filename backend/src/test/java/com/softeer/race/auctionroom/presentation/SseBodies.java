package com.softeer.race.auctionroom.presentation;

import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;

// 배달이 부른 스레드를 떠나 일꾼에서 돌므로 내보낸 직후에 읽으면 본문이 아직 비어 있다
final class SseBodies {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final long POLL_MILLIS = 5L;

    private SseBodies() {
    }

    // 못 기다리면 마지막 본문을 그대로 준다, 여기서 던지면 무엇이 안 왔는지가 아니라 시간 초과만 보인다
    static String awaitUntil(MvcResult result, Predicate<String> arrived) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        String body = read(result);

        while (!arrived.test(body) && System.nanoTime() < deadline) {
            sleep();
            body = read(result);
        }

        return body;
    }

    static String read(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException("배달을 기다리는 중 끊겼다", e);
        }
    }
}
