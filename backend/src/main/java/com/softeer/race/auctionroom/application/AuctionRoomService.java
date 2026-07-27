package com.softeer.race.auctionroom.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auctionroom.domain.*;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.AUCTION_ROOM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AuctionRoomService {

    private final AuctionRoomRepository auctionRoomRepository;
    private final RoomBidRepository roomBidRepository;
    private final RoomPresence roomPresence;
    private final Clock clock;

    // 호가창에 보일 건수
    private static final int RECENT_BID_LIMIT = 20;

    /**
     * 경매방 현황, 조회가 곧 접속 기록이 된다
     */
    @Transactional(readOnly = true)
    public AuctionRoomView enterRoom(long auctionId, long userId) {
        Auction auction = auctionRoomRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);

        AuctionRoomSnapshot snapshot = AuctionRoomSnapshot.from(auction);
        RoomPhase phase = snapshot.phaseAt(now);

        int connectedCount = 0;

        if (phase.isPresenceCounted()) {
            roomPresence.markPresent(auctionId, userId, now);
            connectedCount = roomPresence.countPresent(auctionId, now);
        }

        int bidderCount = roomBidRepository.countBidders(auctionId);

        List<RecentBid> recentBids = roomBidRepository.findRecentBids(auctionId, Limit.of(RECENT_BID_LIMIT));

        return AuctionRoomView.of(
                auctionId, phase, snapshot, connectedCount, bidderCount, recentBids, now);
    }
}