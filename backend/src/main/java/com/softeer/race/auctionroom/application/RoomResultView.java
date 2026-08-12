package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionOutcome;
import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.auctionroom.domain.BidStats;
import com.softeer.race.auctionroom.domain.BidderStanding;
import com.softeer.race.auctionroom.domain.PricePoint;
import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.auctionroom.domain.VehicleSummary;

import java.time.LocalDateTime;
import java.util.List;

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
        boolean sellerIsMine,
        BidderStanding standing,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime resultViewingEndsAt,
        LocalDateTime serverTime,
        int extensionCount,
        BidStats stats,
        List<PricePointView> priceCurve
) {

    // 결과는 더 이상 바뀌지 않으므로 접속자 수는 담지 않는다
    // 서버 시각은 결과값이 아니라 화면이 남은 열람 시간을 세는 기준이라 예외로 담는다
    static RoomResultView of(
            AuctionRoomDetail detail, AuctionOutcome outcome, BidStats stats,
            long viewerId, BidderStanding standing, List<PricePoint> priceCurve,
            List<String> imageUrls, LocalDateTime serverTime) {

        return new RoomResultView(
                detail.auctionId(),
                outcome,
                detail.vehicle(imageUrls),
                detail.startPrice(),
                detail.winningPrice().orElse(null),
                detail.winnerName().orElse(null),
                detail.isWonBy(viewerId),
                detail.isSoldBy(viewerId),
                standing,
                detail.startTime(),
                detail.currentEndTime(),
                detail.resultViewingEndsAt(),
                serverTime,
                detail.extensionCount(),
                stats,
                // 마감이 어느 입찰에 밀렸는지는 저장돼 있지 않아 점마다 되짚어 묻는다
                priceCurve.stream()
                        .map(point -> PricePointView.of(point, viewerId,
                                point.pushedDeadline(detail.startTime())))
                        .toList());
    }
}
