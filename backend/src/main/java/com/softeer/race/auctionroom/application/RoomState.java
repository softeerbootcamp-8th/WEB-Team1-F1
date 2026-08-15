package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
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
        int connectedCount,
        BidCounts bidCounts,
        MaskedName winnerName,
        List<RecentBid> recentBids
) {

    /**
     * 한 번 읽어 온 값과 방에 열려 있는 구독 수로 방 현황을 조립한다
     */
    // 구독 수를 접속자 수로 볼지는 단계가 정한다, 조회와 방송이 여기를 함께 지나므로 판정이 하나로 남는다
    static RoomState of(RoomSnapshot snapshot, int openSubscriptions) {
        AuctionRoomDetail detail = snapshot.detail();

        return new RoomState(
                detail.auctionId(),
                snapshot.phase(),
                detail.startPrice(),
                detail.currentPrice(),
                detail.openAt(),
                detail.startAt(),
                detail.endAt(),
                snapshot.serverTime(),
                snapshot.phase().allowsConnection() ? openSubscriptions : 0,
                snapshot.bidCounts(),
                detail.winnerName().orElse(null),
                snapshot.recentBids());
    }
}
