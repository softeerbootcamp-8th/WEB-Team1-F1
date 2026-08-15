package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionOutcome;
import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.*;

@Service
@RequiredArgsConstructor
public class AuctionRoomService {

    private final AuctionRoomReader auctionRoomReader;
    private final RoomChannel roomChannel;
    private final Clock clock;

    /**
     * 경매방 현황, 조회한 사람의 입찰과 낙찰 여부까지 판정된 상태
     */
    @Transactional(readOnly = true)
    public AuctionRoomView enterRoom(long auctionId, long userId) {
        RoomSnapshot snapshot = auctionRoomReader.find(auctionId)
                .orElseThrow(() -> new BusinessException(ROOM_NOT_FOUND));

        // 열리지 않은 방과 끝난 방은 여기서 걸린다,
        // 화면은 사유를 보고 개장 안내나 결과로 옮겨간다
        snapshot.phase().entryRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        // 조회는 접속이 아니다, 접속자는 열려 있는 구독으로만 센다
        return AuctionRoomView.of(userId, snapshot, roomChannel.countViewers(auctionId),
                auctionRoomReader.findPhotoUrls(auctionId),
                auctionRoomReader.findKeywords(snapshot.detail().vehicleId()));
    }

    /**
     * 아직 열리지 않은 경매방의 안내, 입장 가능 시각을 화면에 보이기 위한 것
     */
    @Transactional(readOnly = true)
    public RoomOpening readOpening(long auctionId) {
        AuctionRoomDetail detail = auctionRoomReader.findDetail(auctionId)
                .orElseThrow(() -> new BusinessException(ROOM_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);

        // 이미 열린 방에는 안내할 것이 없다, 화면은 방 조회로 옮겨간다
        // 조립보다 먼저 묻는다, 안내는 열리지 않은 방에만 뜻이 있으므로 거절될 요청에는 만들 것이 없다
        detail.phaseAt(now).openingRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        return RoomOpening.of(detail, auctionRoomReader.findPhotoUrls(auctionId),
                auctionRoomReader.findKeywords(detail.vehicleId()), now);
    }

    /**
     * 끝난 경매의 결과 요약, 조회한 사람이 낙찰자인지까지 판정된 상태
     */
    @Transactional(readOnly = true)
    public RoomResultView readResult(long auctionId, long viewerId) {
        AuctionRoomDetail detail = auctionRoomReader.findDetail(auctionId)
                .orElseThrow(() -> new BusinessException(ROOM_NOT_FOUND));

        // 확정 전에 답하면 낙찰된 경매를 유찰이라 말하게 된다, 집계도 여기서 걸러 읽지 않는다
        AuctionOutcome outcome = detail.outcome()
                .orElseThrow(() -> new BusinessException(ROOM_RESULT_NOT_READY));

        return RoomResultView.of(detail, outcome, auctionRoomReader.findBidCounts(auctionId), viewerId,
                auctionRoomReader.findStanding(auctionId, viewerId).orElse(null),
                auctionRoomReader.findPriceCurve(auctionId),
                auctionRoomReader.findPhotoUrls(auctionId),
                auctionRoomReader.findKeywords(detail.vehicleId()), LocalDateTime.now(clock));
    }
}