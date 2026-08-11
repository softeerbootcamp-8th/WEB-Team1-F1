package com.softeer.race.auctionlist.application;

import java.util.HashMap;
import java.util.Map;

// 지난번에 보낸 방별 시청자 수를 기억했다가 달라진 것만 골라낸다
// 스케줄러 한 스레드에서만 불린다, 자료구조를 동시성용으로 바꿔도 맵 전체에 걸친 갱신은 원자적이지 않다
class AudienceSnapshot {

    private final Map<Long, Integer> lastSent = new HashMap<>();

    Map<Long, Integer> advanceTo(Map<Long, Integer> current) {
        Map<Long, Integer> changed = new HashMap<>();

        for (Map.Entry<Long, Integer> now : current.entrySet()) {
            if (!now.getValue().equals(lastSent.get(now.getKey()))) {
                changed.put(now.getKey(), now.getValue());
            }
        }

        // 마지막 사람이 나가면 채널에서 방 자체가 빠진다, 잠자코 있으면 화면이 옛 수에서 멈춘다
        for (Long vanished : lastSent.keySet()) {
            if (!current.containsKey(vanished)) {
                changed.put(vanished, 0);
            }
        }

        lastSent.clear();
        lastSent.putAll(current);

        return changed;
    }
}
