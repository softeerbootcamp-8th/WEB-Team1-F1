package com.softeer.race.auctionroom.application;

import com.softeer.race.auctionroom.domain.RoomPhase;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.softeer.race.auctionroom.domain.RoomErrorCode.ROOM_NOT_FOUND;

/**
 * 경매방 현황을 열려 있는 구독으로 흘려보내는 서비스
 */
// @Transactional 을 붙이지 않는다, 브로드캐스트가 소켓 쓰기라 안 받아 가는 상대 하나에 커넥션이 묶인다
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomStreamService {

    private final RoomReader roomReader;
    private final RoomChannel roomChannel;

    /**
     * 구독을 등록하고 방 전체에 늘어난 사람 수를 보낸다, 다시 붙은 구독에는 마지막 현황도 보낸다
     */
    public void subscribe(long auctionId, RoomSubscription subscription, boolean reconnected) {
        // 연결을 열어 두지 않는 단계의 구독이 채널에 남지 않도록 등록 전에 판정한다
        RoomPhase phase = roomReader.findPhase(auctionId)
                .orElseThrow(() -> new BusinessException(ROOM_NOT_FOUND));

        phase.streamRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        roomChannel.subscribe(auctionId, subscription);

        // 첫 화면은 방 조회가 이미 줬다, 여기서 현황을 다시 보내면 같은 것을 두 번 그린다
        // 다시 붙은 화면은 그 조회를 거치지 않아 다음 사건까지 낡은 채로 남으므로 여기서 따라잡게 한다
        if (reconnected) {
            roomChannel.catchUp(auctionId, subscription);
        }

        broadcastViewerCount(auctionId);
    }

    /**
     * 구독을 방에서 빼고 남은 구독에 줄어든 사람 수를 보낸다
     */
    public void unsubscribe(long auctionId, RoomSubscription subscription) {
        // 걷어내기와 방 끊기가 먼저 빼 간 뒤에도 이 콜백은 돌아온다, 그때도 보내면 같은 수가 두 번 나간다
        if (roomChannel.unsubscribe(auctionId, subscription)) {
            broadcastViewerCount(auctionId);
        }
    }

    /**
     * 끊긴 구독을 걷어내고, 사람이 빠진 방에는 줄어든 사람 수를 보낸다
     */
    public void sweepClosedSubscriptions() {
        // 한 방의 실패를 그 방에 가둔다, 여기서만 옳은 정책이라 방송 자체는 계속 던지게 둔다
        for (long auctionId : roomChannel.sweepClosed()) {
            try {
                broadcastViewerCount(auctionId);
            } catch (Exception e) {
                log.warn("경매방 사람 수 전송 실패, 경매 {}", auctionId, e);
            }
        }
    }

    /**
     * 연결을 열어 두지 않는 단계가 된 방의 구독을 서버가 끊는다
     */
    // 마감 방송이 이미 끊고 지나가므로 여기 걸리는 것은 그 방송이 유실된 방뿐이다
    public void closeStreamEndedRooms() {
        for (long auctionId : roomChannel.subscribedRooms()) {
            // 한 방의 실패를 그 방에 가둔다, 남은 방은 이번 주기에 그대로 정리한다
            try {
                if (connectionsAreOver(auctionId)) {
                    roomChannel.closeRoom(auctionId);
                }
            } catch (Exception e) {
                log.warn("경매방 연결 종료 실패, 경매 {}", auctionId, e);
            }
        }
    }

    /**
     * 방에 열려 있는 구독에 현황을 다시 보낸다, 보는 사람이 없으면 조회도 하지 않는다
     */
    public void refresh(long auctionId) {
        if (roomChannel.viewerCount(auctionId) == 0) {
            return;
        }

        // 구독이 열려 있는 사이에 경매글이 내려갔다면 보낼 현황이 없다
        roomReader.find(auctionId)
                .ifPresent(this::broadcast);
    }

    // 경매글이 내려가 단계를 알 수 없으면 끊을 근거도 없으므로 그대로 둔다
    private boolean connectionsAreOver(long auctionId) {
        return roomReader.findPhase(auctionId)
                .map(phase -> !phase.allowsConnection())
                .orElse(false);
    }

    // 사람 수는 DB 를 읽지 않는다, 채널 메모리에만 있고 세는 것과 번호를 매기는 것을 채널이 함께 해 준다
    private void broadcastViewerCount(long auctionId) {
        roomChannel.broadcast(auctionId, roomChannel.readViewerCount(auctionId));
    }

    private void broadcast(RoomSnapshot snapshot) {
        long auctionId = snapshot.detail().auctionId();

        roomChannel.broadcast(auctionId, RoomState.of(snapshot));

        // 마감됐다는 마지막 현황까지 받고 나서 끊는다, 이 순서라야 화면이 끊김을 결과로 읽는다
        if (!snapshot.phase().allowsConnection()) {
            roomChannel.closeRoom(auctionId);
        }
    }
}
