package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.BidStats;
import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.auctionroom.domain.RoomPhase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매방을 한 번 읽어 온 결과, 여기서 조회 응답과 브로드캐스트 현황이 각각 조립된다
 */
record RoomQueryResult(
        AuctionRoomDetail detail,
        RoomPhase phase,
        BidStats stats,
        List<RecentBid> recentBids,
        LocalDateTime serverTime
) {
}