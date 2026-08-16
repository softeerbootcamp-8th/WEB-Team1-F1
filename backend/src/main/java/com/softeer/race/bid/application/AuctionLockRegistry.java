package com.softeer.race.bid.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// 서버 한 대 전제라서, 늘리면 대마다 다른 잠금을 보게 되어 AuctionBidGate 와 함께 Redis 로 옮겨야 한다.
@Component
public class AuctionLockRegistry {

    // 항목을 지우지 않는다 - 지우는 순간과 집어 가는 순간이 겹치면 서로 다른 잠금을 들고 같은 경매에 들어간다.
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock obtain(long auctionId) {
        return locks.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }
}
