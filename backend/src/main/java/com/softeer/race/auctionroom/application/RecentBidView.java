package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RecentBid;

/**
 * 호가창 한 줄, 내 입찰인지까지 판정된 상태
 */
public record RecentBidView(
        RoomStateBid bid,
        boolean mine
) {

    // 호가 자체는 방 전체에 나가는 것과 같은 값을 쓴다, 여기서 얹히는 건 보는 사람 기준의 판정 하나뿐이다
    static RecentBidView of(RecentBid bid, long viewerId) {
        return new RecentBidView(RoomStateBid.from(bid), bid.isMine(viewerId));
    }
}
