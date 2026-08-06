package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionOutcome;
import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.MaskedName;
import com.softeer.race.auctionroom.domain.VehicleSummary;

/**
 * 끝난 경매의 결과 요약, 조회한 사람이 낙찰자인지까지 판정된 상태
 */
public record RoomResultView(
        long auctionId,
        AuctionOutcome outcome,
        VehicleSummary vehicle,
        long startPrice,
        Long winningPrice,
        MaskedName winnerName,
        boolean winnerIsMine,
        long bidCount
) {

    // 결과는 더 이상 바뀌지 않으므로 접속자 수도 서버 시각도 담지 않는다
    static RoomResultView of(
            AuctionRoomDetail detail, AuctionOutcome outcome, long bidCount, long viewerId) {

        return new RoomResultView(
                detail.auctionId(),
                outcome,
                detail.vehicle(),
                detail.startPrice(),
                detail.winningPrice().orElse(null),
                detail.winnerName().orElse(null),
                detail.isWonBy(viewerId),
                bidCount);
    }
}
