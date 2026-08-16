package com.softeer.race.bid.application;

import com.softeer.race.bid.domain.AuctionBidSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 잠금 앞에서 확실히 떨어질 입찰을 커넥션 없이 걸러 내는 성능 장치로, 정합성은 잠금 안 판정이 책임진다.
 */
@Component
@RequiredArgsConstructor
public class AuctionBidGate {

    private final BidIncrementService bidIncrementService;
    private final Clock clock;

    private final Map<Long, AuctionBidSnapshot> snapshots = new ConcurrentHashMap<>();

    public void rejectIfDoomed(long auctionId, long bidderId, long amount) {
        AuctionBidSnapshot snapshot = snapshots.get(auctionId);

        if (snapshot == null) {
            return; // 모르는 경매는 판정하지 않는다, 판정은 잠금 안의 몫이다.
        }

        snapshot.rejectIfDoomed(
                bidIncrementService.loadTable(), bidderId, amount, LocalDateTime.now(clock));
    }

    // 커밋 뒤 그리고 잠금을 놓기 전에 불러야 한다 - 어기면 롤백된 값이 남거나 낡은 사본으로 통과시킨다.
    public void record(long auctionId, AuctionBidSnapshot snapshot) {
        snapshots.put(auctionId, snapshot);
    }

    // DB 를 되감는 쪽(테스트)은 이걸 같이 되감아야 이전 현재가가 새 데이터의 정상 입찰을 거절하지 않는다.
    public void clear() {
        snapshots.clear();
    }
}
