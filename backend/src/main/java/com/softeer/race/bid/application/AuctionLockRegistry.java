package com.softeer.race.bid.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// 서버 한 대 전제라서, 늘리면 대마다 다른 잠금을 보게 되어 AuctionBidGate 와 함께 Redis 로 옮겨야 한다.
@Component
public class AuctionLockRegistry {

    // 잠금과 그것을 잡고 있거나 기다리는 스레드 수. compute 안에서만 만들고 바꾼다.
    private record Entry(ReentrantLock lock, int users) { }

    // 키가 요청 경로의 숫자라 로그인한 사용자가 임의 값으로 무한히 만들 수 있다.
    private final Map<Long, Entry> locks = new ConcurrentHashMap<>();

    // 사용자 수가 0 이 되는 순간에만 항목이 지워지므로, 겹치는 요청들은 언제나 같은 잠금을 본다.
    public ReentrantLock acquire(long auctionId) {
        return locks.compute(auctionId, (id, entry) ->
                entry == null
                        ? new Entry(new ReentrantLock(), 1)
                        : new Entry(entry.lock(), entry.users() + 1)
        ).lock();
    }

    // 사용 등록을 반납한다. 마지막 사용자였으면 항목이 함께 사라진다.
    public void release(long auctionId) {
        locks.compute(auctionId, (id, entry) ->
                entry.users() == 1 ? null : new Entry(entry.lock(), entry.users() - 1));
    }
}
