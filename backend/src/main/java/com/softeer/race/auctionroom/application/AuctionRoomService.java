package com.softeer.race.auctionroom.application;

import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.AUCTION_ROOM_NOT_FOUND;

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

        // 조회는 접속이 아니다, 접속자는 열려 있는 구독 수로만 센다
        int connectedCount = result.phase().allowsConnection()
                ? roomChannel.countSubscribers(auctionId) : 0;

        return AuctionRoomView.of(userId, result, connectedCount);
    }
}