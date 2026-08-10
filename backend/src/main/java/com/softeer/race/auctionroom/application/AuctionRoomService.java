package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.AuctionOutcome;
import com.softeer.race.auctionroom.domain.AuctionRoomDetail;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.*;

@Service
@RequiredArgsConstructor
public class AuctionRoomService {

    private final AuctionRoomReader auctionRoomReader;
    private final RoomChannel roomChannel;

    /**
     * 경매방 현황, 조회한 사람의 입찰과 낙찰 여부까지 판정된 상태
     */
    @Transactional(readOnly = true)
    public AuctionRoomView enterRoom(long auctionId, long userId) {
        RoomQueryResult result = auctionRoomReader.find(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        // 열리지 않은 방과 끝난 방은 여기서 걸린다,
        // 화면은 사유를 보고 개장 안내나 결과로 옮겨간다
        result.phase().entryRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        // 조회는 접속이 아니다, 접속자는 열려 있는 구독 수로만 센다
        return AuctionRoomView.of(userId, result, roomChannel.countSubscribers(auctionId),
                auctionRoomReader.findPhotoUrls(auctionId));
    }

    /**
     * 아직 열리지 않은 경매방의 안내, 입장 가능 시각을 화면에 보이기 위한 것
     */
    @Transactional(readOnly = true)
    public RoomOpening readOpening(long auctionId) {
        RoomOpening opening = auctionRoomReader.findOpening(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        // 이미 열린 방에는 안내할 것이 없다, 화면은 방 조회로 옮겨간다
        opening.phase().openingRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        return opening;
    }

    /**
     * 끝난 경매의 결과 요약, 조회한 사람이 낙찰자인지까지 판정된 상태
     */
    @Transactional(readOnly = true)
    public RoomResultView readResult(long auctionId, long viewerId) {
        AuctionRoomDetail detail = auctionRoomReader.findDetail(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        // 확정 전에 답하면 낙찰된 경매를 유찰이라 말하게 된다, 집계도 여기서 걸러 읽지 않는다
        AuctionOutcome outcome = detail.outcome()
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_ENDED));

        return RoomResultView.of(detail, outcome, auctionRoomReader.countBids(auctionId), viewerId,
                auctionRoomReader.findPhotoUrls(auctionId));
    }
}