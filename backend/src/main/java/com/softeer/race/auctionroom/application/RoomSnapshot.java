package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RoomDetail;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.auctionroom.domain.RoomPhase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매방을 한 번에 읽어 찍은 한 시점, 여기서 조회 응답과 브로드캐스트 현황이 각각 조립된다
 */
record RoomSnapshot(
        RoomDetail detail,
        BidCounts bidCounts,
        List<RecentBid> recentBids,
        LocalDateTime serverTime
) {

    // 저장하면 단계와 기준 시각이 어긋난 객체를 만들 수 있고, 계산하면 그럴 수 없다
    RoomPhase phase() {
        return detail.phaseAt(serverTime);
    }
}
