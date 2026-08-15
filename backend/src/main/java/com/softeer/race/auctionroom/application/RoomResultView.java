package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionOutcome;
import com.softeer.race.auctionroom.domain.RoomDetail;
import com.softeer.race.auctionroom.domain.BidCounts;
import com.softeer.race.auctionroom.domain.BidderStanding;
import com.softeer.race.auctionroom.domain.BidPoint;
import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.vehicle.domain.VehicleKeyword;

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
        BidderStanding viewerStanding,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime resultViewingEndsAt,
        LocalDateTime serverTime,
        int extensionCount,
        BidCounts bidCounts,
        List<BidPointView> priceCurve
) {

    // 결과는 더 이상 바뀌지 않으므로 접속자 수는 담지 않는다
    // 서버 시각은 결과값이 아니라 화면이 남은 열람 시간을 세는 기준이라 예외로 담는다
    static RoomResultView of(
            RoomDetail detail, AuctionOutcome outcome, BidCounts bidCounts,
            long viewerId, BidderStanding viewerStanding, List<BidPoint> priceCurve,
            List<String> imageUrls, List<VehicleKeyword> keywords, LocalDateTime serverTime) {

        return new RoomResultView(
                detail.auctionId(),
                outcome,
                VehicleSummary.of(detail, imageUrls, keywords),
                detail.startPrice(),
                detail.winningPrice().orElse(null),
                detail.winnerName().orElse(null),
                detail.isWonBy(viewerId),
                detail.isSoldBy(viewerId),
                viewerStanding,
                detail.startAt(),
                detail.endAt(),
                detail.resultViewingEndsAt(),
                serverTime,
                detail.extensionCount(),
                bidCounts,
                priceCurve.stream()
                        .map(point -> BidPointView.of(point, viewerId, detail.startAt()))
                        .toList());
    }
}
