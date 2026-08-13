package com.softeer.race.auctionroom.application;

import com.softeer.race.vehicle.domain.VehicleKeyword;

import java.util.List;

/**
 * 경매방 조회 결과, 방 현황에 조회한 사람 기준의 판정을 얹은 것
 */
public record AuctionRoomView(
        RoomState state,
        VehicleSummary vehicle,
        boolean winnerIsMine,
        boolean sellerIsMine,
        List<RecentBidView> recentBids
) {

    // 방 현황은 브로드캐스트와 같은 조립을 그대로 쓴다, 여기서 갈라지면 열어 둔 화면과 방금 들어온 화면이 달라진다
    // 호가만 다시 훑는다, 내 입찰 표시는 보는 사람마다 달라서 방 현황에 담을 수 없다
    static AuctionRoomView of(long viewerId, RoomQueryResult result, int connectedCount,
                              List<String> imageUrls, List<VehicleKeyword> keywords) {
        return new AuctionRoomView(
                RoomState.of(result, connectedCount),
                VehicleSummary.of(result.detail(), imageUrls, keywords),
                result.detail().isWonBy(viewerId),
                result.detail().isSoldBy(viewerId),
                result.recentBids().stream().map(bid -> RecentBidView.of(bid, viewerId)).toList());
    }
}