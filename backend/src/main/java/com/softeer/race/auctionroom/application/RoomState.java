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
        long startPrice,
        long currentPrice,
        LocalDateTime openAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime serverTime,
        int viewerCount,
        BidCounts bidCounts,
        MaskedName winnerName,
        List<RecentBid> recentBids
) {

    /**
     * 이미 내보낸 현황보다 낡았는지, 아직 내보낸 것이 없거나 값이 같으면 낡지 않은 것으로 본다
     */
    // 현재가는 락 안에서 더 높은 금액만 받아 단조 증가하므로, 낮은 값을 든 현황이 더 이른 시점을 읽은 것이다
    // 같은 값은 통과시킨다, 사람 수와 호가창도 함께 실려 나가 막으면 그것들이 안 갱신된다
    // 같은 값 둘의 순서는 가리지 못한다, 단계나 연장된 마감이 되돌아가는 것이 그 틈으로 지나갈 수 있다
    public boolean isStalerThan(RoomState broadcasted) {
        return broadcasted != null && currentPrice < broadcasted.currentPrice();
    }

    /**
     * 한 번 읽어 온 값과 방을 보고 있는 사람 수로 방 현황을 조립한다
     */
    // 사람 수를 내보낼지는 단계가 정한다, 조회와 방송이 여기를 함께 지나므로 판정이 하나로 남는다
    static RoomState of(RoomSnapshot snapshot, int viewerCount) {
        RoomDetail detail = snapshot.detail();

        return new RoomState(
                detail.auctionId(),
                snapshot.phase(),
                detail.startPrice(),
                detail.currentPrice(),
                detail.openAt(),
                detail.startAt(),
                detail.endAt(),
                snapshot.serverTime(),
                snapshot.phase().allowsConnection() ? viewerCount : 0,
                snapshot.bidCounts(),
                detail.winnerName().orElse(null),
                snapshot.recentBids());
    }
}
