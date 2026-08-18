package com.softeer.race.auctionlist.presentation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// 밀린 방송을 경매마다 최신 한 건만 들고 있는 칸이다. 스레드도 소켓도 모르고 규약만 정한다
// 주인은 넣거나 요청한 뒤에 claimDrain 을 부르고, 참을 받았으면 renewDrain 이 거짓을 줄 때까지 돈다
class AuctionListMailbox {

    private final Lock lock = new ReentrantLock();

    // 넣은 순서를 지키는 지도다, 여러 경매가 함께 밀려 있을 때 나가는 순서가 뒤집히면 안 된다
    private final Map<Key, AuctionListMessage> pending = new LinkedHashMap<>();

    // 칸이 비어 있을 때 순서를 가리는 기준이다. 없으면 높은 값을 내보낸 뒤 온 낮은 값이 빈 칸에 들어간다
    // 내보낸 것을 통째로 들면 구독 하나가 30분 동안 본 경매마다 카드 한 벌을 쥔다, 그래서 표식만 남긴다
    private final Map<Key, Long> sentMarks = new HashMap<>();

    private boolean pingRequested;

    // 끝내기도 줄을 선다, 마지막 카드를 내보낸 직후에 끝내야 그것이 사라지지 않는다
    private boolean closeRequested;

    private boolean draining;

    // 경매가 다르면 다른 칸이다, 종류로만 접으면 한 경매의 카드가 다른 경매의 카드를 덮는다
    private record Key(Class<? extends AuctionListMessage> type, long auctionId) {
    }

    void offer(AuctionListMessage message) {
        lock.lock();

        try {
            Key key = keyOf(message);

            if (message.mark() < newestMark(key)) {
                return;
            }

            pending.put(key, message);
        } finally {
            lock.unlock();
        }
    }

    // 구독 하나에 일꾼 하나만 붙는 것이 여기서 정해진다, SseEmitter 에 두 스레드가 쓰지 않는 근거가 이것이다
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

    List<AuctionListMessage> drainMessages() {
        lock.lock();

        try {
            if (pending.isEmpty()) {
                return List.of();
            }

            List<AuctionListMessage> taken = List.copyOf(pending.values());

            pending.forEach((key, message) -> sentMarks.put(key, message.mark()));
            pending.clear();

            return taken;
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

    // 카드와 같은 일꾼을 타야 한다
    void requestPing() {
        lock.lock();

        try {
            pingRequested = true;
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

    private boolean isEmpty() {
        return pending.isEmpty() && !pingRequested && !closeRequested;
    }

    // 밀려 있는 것이 있으면 그것이 기준이고, 없으면 마지막으로 내보낸 표식이 기준이다
    private long newestMark(Key key) {
        AuctionListMessage waiting = pending.get(key);

        if (waiting != null) {
            return waiting.mark();
        }

        return sentMarks.getOrDefault(key, Long.MIN_VALUE);
    }

    private static Key keyOf(AuctionListMessage message) {
        return new Key(message.getClass(), message.auctionId());
    }
}
