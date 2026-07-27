package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 경매방 화면을 구성하는 경매 정보
 */
public record AuctionRoomSnapshot(
        long startPrice, // 얘는 항상있어서 원시값으로 설정가능
        Long currentPrice, // 첫 입찰이 들어와야 생기기 때문에 null도 가능.
        LocalDateTime roomOpenAt,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    // 마감 후 결과를 확인할 수 있는 구간
    private static final Duration RESULT_VIEWING = Duration.ofMinutes(5);

    public static AuctionRoomSnapshot from(Auction auction) {
        return new AuctionRoomSnapshot(
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.getRoomOpenAt(),
                auction.getStartTime(),
                auction.getCurrentEndTime());
    }

    /**
     * 주어진 시각 기준의 방 단계
     */
    public RoomPhase phaseAt(LocalDateTime now) {
        if (now.isBefore(roomOpenAt)) {
            return RoomPhase.NOT_OPEN;
        }
        if (now.isBefore(startTime)) {
            return RoomPhase.WAITING;
        }
        if (now.isBefore(endTime)) {
            return RoomPhase.LIVE;
        }
        if (now.isBefore(endTime.plus(RESULT_VIEWING))) {
            return RoomPhase.RESULT;
        }
        return RoomPhase.CLOSED;
    }

    /**
     * 화면에 보일 현재가, 입찰이 없으면 시작가
     */
    public long displayPrice() {
        return currentPrice != null ? currentPrice : startPrice;
    }
}