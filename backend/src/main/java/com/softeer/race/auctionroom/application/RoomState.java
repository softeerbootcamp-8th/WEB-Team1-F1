package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RoomDetail;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.common.domain.MaskedName;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방에 있는 누구에게나 같은 경매방 현황, 브로드캐스트 단위
 */
public record RoomState(
        long auctionId,
        RoomPhase phase,
        long currentPrice,
        LocalDateTime endAt,
        LocalDateTime serverTime,
        BidCounts bidCounts,
        MaskedName winnerName,
        List<RecentBid> recentBids
) implements RoomMessage {

    /**
     * 이미 내보낸 현황보다 낡았는지, 아직 내보낸 것이 없거나 값이 같으면 낡지 않은 것으로 본다
     */
    // 현재가는 락 안에서 더 높은 금액만 받아 단조 증가하므로, 낮은 값을 든 현황이 더 이른 시점을 읽은 것이다
    // 같은 값은 통과시킨다, 호가창과 집계와 단계도 함께 실려 나가 막으면 그것들이 안 갱신된다
    // 같은 값 둘의 순서는 가리지 못한다, 단계나 연장된 마감이 되돌아가는 것이 그 틈으로 지나갈 수 있다
    @Override
    public boolean isStalerThan(RoomMessage sent) {
        return sent instanceof RoomState broadcasted && currentPrice < broadcasted.currentPrice();
    }

    /**
     * 한 번 읽어 온 값으로 방 현황을 조립한다
     */
    static RoomState of(RoomSnapshot snapshot) {
        RoomDetail detail = snapshot.detail();

        return new RoomState(
                detail.auctionId(),
                snapshot.phase(),
                detail.currentPrice(),
                detail.endAt(),
                snapshot.serverTime(),
                snapshot.bidCounts(),
                detail.winnerName().orElse(null),
                snapshot.recentBids());
    }
}
