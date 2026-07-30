package com.softeer.race.auctionroom.application;

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
        // 쿼리 셋이 한 트랜잭션에서 같은 스냅샷을 봐야한다.
        AuctionRoomDetail detail = auctionRoomRepository.findDetailById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);

        RoomPhase phase = detail.snapshot().phaseAt(now);

        BidStats stats = roomBidRepository.findStats(auctionId);

        List<RecentBid> recentBids = roomBidRepository.findRecentBids(auctionId, Limit.of(RECENT_BID_LIMIT));

        int connectedCount = 0;

        if (phase.isPresenceCounted()) {
            roomPresence.markPresent(auctionId, userId, now);
            connectedCount = roomPresence.countPresent(auctionId, now);
        }

        return AuctionRoomView.of(
                auctionId, userId, phase, detail, connectedCount, stats, recentBids, now);
    }
}