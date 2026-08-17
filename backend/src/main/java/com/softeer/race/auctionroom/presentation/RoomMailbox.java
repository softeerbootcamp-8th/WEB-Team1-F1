package com.softeer.race.auctionroom.presentation;

import com.softeer.race.auctionroom.application.RoomMessage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// 밀린 현황을 종류마다 최신 한 건만 들고 있는 칸이다. 스레드도 소켓도 모르고 규약만 정한다.
// 주인은 offer 나 requestPing 뒤에 claimDrain 을 부르고, 참을 받았으면 renewDrain 이 거짓을 줄 때까지 돈다.
class RoomMailbox {

    private final Lock lock = new ReentrantLock();

    // 넣은 순서를 지키는 지도다, 종류 둘이 함께 밀려 있을 때 나가는 순서가 뒤집히면 안 된다
    private final Map<Class<? extends RoomMessage>, RoomMessage> pending = new LinkedHashMap<>();

    // 칸이 비어 있을 때 낡음을 가리는 기준이다. 없으면 100 을 보낸 뒤 온 90 이 빈 칸에 들어가 현재가가 되돌아간다
    private final Map<Class<? extends RoomMessage>, RoomMessage> sent = new HashMap<>();

    private boolean pingRequested;

    // 끝내기도 줄을 선다, 마감 현황을 내보낸 직후에 방을 끊으므로 먼저 끝내면 그 현황이 사라진다
    private boolean closeRequested;

    private boolean draining;

    void offer(RoomMessage message) {
        lock.lock();

        try {
            RoomMessage newest = pending.getOrDefault(message.getClass(), sent.get(message.getClass()));

            if (message.isStalerThan(newest)) {
                return;
            }

            pending.put(message.getClass(), message);
        } finally {
            lock.unlock();
        }
    }

    // 현황과 같은 일꾼을 타야 한다, SseEmitter 는 두 스레드가 동시에 쓰면 안 된다
    void requestPing() {
        lock.lock();

        try {
            pingRequested = true;
        } finally {
            lock.unlock();
        }
    }

    // 구독 하나에 일꾼 하나만 붙는 것이 여기서 정해진다, 그래서 대기열 길이의 상한이 구독 수다
    boolean claimDrain() {
        lock.lock();

        try {
            if (draining || isEmpty()) {
                return false;
            }

            draining = true;

            return true;
        } finally {
            lock.unlock();
        }
    }

    List<RoomMessage> drainMessages() {
        lock.lock();

        try {
            if (pending.isEmpty()) {
                return List.of();
            }

            List<RoomMessage> taken = List.copyOf(pending.values());

            sent.putAll(pending);
            pending.clear();

            return taken;
        } finally {
            lock.unlock();
        }
    }

    boolean drainPing() {
        lock.lock();

        try {
            boolean requested = pingRequested;
            pingRequested = false;

            return requested;
        } finally {
            lock.unlock();
        }
    }

    void requestClose() {
        lock.lock();

        try {
            closeRequested = true;
        } finally {
            lock.unlock();
        }
    }

    boolean drainClose() {
        lock.lock();

        try {
            boolean requested = closeRequested;
            closeRequested = false;

            return requested;
        } finally {
            lock.unlock();
        }
    }

    // 잡은 것을 갱신하거나 내려놓는다, 참이면 그사이 새로 들어온 것이 있어 한 바퀴 더 돈다
    // 내려놓는 것과 넣는 쪽이 그것을 보는 것이 같은 잠금 안이라 마지막 한 건을 놓치지 않는다
    boolean renewDrain() {
        lock.lock();

        try {
            if (!isEmpty()) {
                return true;
            }

            draining = false;

            return false;
        } finally {
            lock.unlock();
        }
    }

    private boolean isEmpty() {
        return pending.isEmpty() && !pingRequested && !closeRequested;
    }
}
