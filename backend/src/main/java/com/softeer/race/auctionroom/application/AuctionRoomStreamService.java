package com.softeer.race.auctionroom.application;

import com.softeer.race.common.config.SchedulingConfig;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.AUCTION_ROOM_NOT_FOUND;
import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_NOT_SUBSCRIBABLE;

/**
 * 경매방 현황을 열려 있는 구독으로 흘려보내는 서비스
 */
// @Transactional 을 붙이지 않는다, 브로드캐스트가 소켓 쓰기라 안 받아 가는 상대 하나에 커넥션이 묶인다
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionRoomStreamService {

    // 나간 사람이 이 시간 안에는 접속자에서 빠진다, 프록시가 유휴 연결을 끊는 것도 이 신호가 막는다
    private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

    private final AuctionRoomReader auctionRoomReader;
    private final RoomChannel roomChannel;

    /**
     * 구독을 등록하고 방 전체에 현황을 보낸다, 새 구독은 첫 현황을 받고 기존 구독은 늘어난 접속자 수를 받는다
     */
    public void subscribe(long auctionId, RoomSubscriber subscriber) {
        // 없는 방과 닫힌 방의 구독이 채널에 남지 않도록 등록 전에 판정한다
        RoomQueryResult result = auctionRoomReader.find(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_ROOM_NOT_FOUND));

        if (!result.phase().allowsConnection()) {
            throw new BusinessException(ROOM_NOT_SUBSCRIBABLE);
        }

        roomChannel.subscribe(auctionId, subscriber);

        // 접속자 수는 메모리에서 오므로 등록한 뒤에 세면 DB 를 다시 읽지 않아도 된다
        broadcast(result);
    }

    /**
     * 구독을 방에서 빼고 남은 구독에 줄어든 접속자 수를 보낸다
     */
    public void unsubscribe(long auctionId, RoomSubscriber subscriber) {
        roomChannel.unsubscribe(auctionId, subscriber);

        refresh(auctionId);
    }

    /**
     * 끊긴 구독을 걷어내고, 사람이 빠진 방에는 줄어든 접속자 수를 보낸다
     */
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, scheduler = SchedulingConfig.ROOM_STREAM)
    public void sweepClosedSubscriptions() {
        // 한 방의 실패를 그 방에 가둔다, 여기서만 옳은 정책이라 refresh 자체는 계속 던지게 둔다
        for (long auctionId : roomChannel.sweepClosed()) {
            try {
                refresh(auctionId);
            } catch (Exception e) {
                log.warn("경매방 현황 갱신 실패, 경매 {}", auctionId, e);
            }
        }
    }

    /**
     * 방에 열려 있는 구독에 현황을 다시 보낸다, 보는 사람이 없으면 조회도 하지 않는다
     */
    public void refresh(long auctionId) {
        if (roomChannel.countSubscribers(auctionId) == 0) {
            return;
        }

        // 구독이 열려 있는 사이에 경매글이 내려갔다면 보낼 현황이 없다
        auctionRoomReader.find(auctionId)
                .ifPresent(this::broadcast);
    }

    private void broadcast(RoomQueryResult result) {
        long auctionId = result.detail().auctionId();

        // 연결을 열어 두지 않는 단계면 남은 구독은 접속자가 아니다, 조회·목록과 같은 판정을 여기서도 한다
        int connectedCount = result.phase().allowsConnection()
                ? roomChannel.countSubscribers(auctionId) : 0;

        roomChannel.broadcast(auctionId, RoomState.of(result, connectedCount));
    }
}
