package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 조회 응답과 브로드캐스트가 같은 값을 봐야 하므로 읽는 곳을 하나로 둔다
@Component
@RequiredArgsConstructor
class AuctionRoomReader {

    private static final int RECENT_BID_LIMIT = 20;

    private final AuctionRoomRepository auctionRoomRepository;
    private final RoomBidRepository roomBidRepository;
    private final Clock clock;

    // 클래스는 패키지 밖에 안 보이지만 메서드는 public 이어야 한다, 프록시가 public 메서드만 자문한다
    @Transactional(readOnly = true)
    public Optional<RoomQueryResult> find(long auctionId) {
        return auctionRoomRepository.findDetailById(auctionId)
                .map(this::readWith);
    }

    // 개장 전 방에는 호가도 집계도 없다, 상세만 한 번 읽는다
    @Transactional(readOnly = true)
    public Optional<RoomOpening> findOpening(long auctionId) {
        LocalDateTime now = LocalDateTime.now(clock);

        return auctionRoomRepository.findDetailById(auctionId)
                .map(detail -> RoomOpening.of(detail, now));
    }

    @Transactional(readOnly = true)
    public Optional<AuctionRoomDetail> findDetail(long auctionId) {
        return auctionRoomRepository.findDetailById(auctionId);
    }

    // 결과에는 건수만 나가지만 집계 쿼리 하나로 둘 다 나오므로 그대로 쓴다
    @Transactional(readOnly = true)
    public long countBids(long auctionId) {
        return roomBidRepository.findStats(auctionId).bidCount();
    }

    private RoomQueryResult readWith(AuctionRoomDetail detail) {
        LocalDateTime now = LocalDateTime.now(clock);

        BidStats stats = roomBidRepository.findStats(detail.auctionId());
        List<RecentBid> recentBids = roomBidRepository.findRecentBids(detail.auctionId(), Limit.of(RECENT_BID_LIMIT));

        return new RoomQueryResult(detail, detail.snapshot().phaseAt(now), stats, recentBids, now);
    }
}