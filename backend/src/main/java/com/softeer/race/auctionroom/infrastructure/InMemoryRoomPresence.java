package com.softeer.race.auctionroom.infrastructure;

import com.softeer.race.auctionroom.domain.RoomPresence;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRoomPresence implements RoomPresence {

    /**
     * 조회 주기가 2초라 다섯 번 놓쳐야 접속자에서 빠짐
     */
    private static final Duration TTL = Duration.ofSeconds(10);

    // 추후에 필요 시 주기 정리를 붙인다
    private final Map<Long, Map<Long, LocalDateTime>> lastSeenByAuction = new ConcurrentHashMap<>();

    @Override
    public void markPresent(long auctionId, long userId, LocalDateTime now) {
        Map<Long, LocalDateTime> lastSeen =
                lastSeenByAuction.computeIfAbsent(auctionId, id -> new ConcurrentHashMap<>());

        lastSeen.put(userId, now);
        lastSeen.values().removeIf(seen -> isExpired(seen, now));
    }

    @Override
    public int countPresent(long auctionId, LocalDateTime now) {
        Map<Long, LocalDateTime> lastSeen = lastSeenByAuction.get(auctionId);

        if (lastSeen == null) {
            return 0;
        }

        return (int) lastSeen.values().stream()
                .filter(seen -> !isExpired(seen, now))
                .count();
    }

    private static boolean isExpired(LocalDateTime lastSeen, LocalDateTime now) {
        return lastSeen.isBefore(now.minus(TTL));
    }
}