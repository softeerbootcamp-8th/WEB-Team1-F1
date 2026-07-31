package com.softeer.race.auctionlist.application.dto;

import com.softeer.race.auctionlist.domain.AuctionListGroup;

import java.time.LocalDateTime;

/**
 * 직전 페이지가 끝난 지점
 * <p>
 * snapshotAt은 첫 페이지의 조회 시각이다. 이후 페이지에서 다시 재지 않고 이 값을 그대로 쓴다.
 * 페이지마다 새로 재면 그 사이 단계가 바뀐 경매가 자리를 옮겨 누락이나 중복이 생긴다.
 * <p>
 * sortAt은 그룹이 정하는 기준 시각이다. 예정은 시작 시각, 나머지는 마감 시각을 담는다.
 */
public record AuctionListCursor(
        LocalDateTime snapshotAt,
        AuctionListGroup group,
        LocalDateTime sortAt,
        long auctionId
) {

    public static AuctionListCursor first(LocalDateTime snapshotAt) {
        AuctionListGroup first = AuctionListGroup.LIVE;
        return new AuctionListCursor(snapshotAt, first, first.startSortAt(), first.startAuctionId());
    }
}
