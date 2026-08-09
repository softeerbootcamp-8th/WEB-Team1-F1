package com.softeer.race.auctionroom.application;

import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.auctionroom.domain.RecentBid;
import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 브로드캐스트에 실리는 호가 한 건, 입찰자를 특정할 값을 담지 않는다
 */
public record RoomStateBid(
        MaskedName bidderName,
        Role role,
        long amount,
        LocalDateTime bidAt
) {

    // 방 전체에 나가는 값이라 식별자를 여기서 끊는다, 이름은 마스킹된 타입 그대로 들고 가 실수로 원본이 섞일 수 없게 한다
    static RoomStateBid from(RecentBid bid) {
        return new RoomStateBid(bid.bidderName(), bid.role(), bid.amount(), bid.bidAt());
    }
}