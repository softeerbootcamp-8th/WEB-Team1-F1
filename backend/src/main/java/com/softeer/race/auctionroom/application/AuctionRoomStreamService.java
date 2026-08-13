package com.softeer.race.auctionroom.application;

import com.softeer.race.common.config.SchedulingConfig;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.softeer.race.auctionroom.domain.AuctionRoomErrorCode.ROOM_NOT_FOUND;

/**
 * 경매방 현황을 열려 있는 구독으로 흘려보내는 서비스
 */
// @Transactional 을 붙이지 않는다, 브로드캐스트가 소켓 쓰기라 안 받아 가는 상대 하나에 커넥션이 묶인다
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionRoomStreamService {

    // 알리지 않고 끊긴 사람이 이 시간 안에는 접속자에서 빠진다, 프록시가 유휴 연결을 끊는 것도 이 신호가 막는다
    // 정상 종료는 해제 콜백이 즉시 빼 가므로 이 주기는 조용히 사라진 연결만 줍는 안전망이다
    // 짧게 잡아도 싸다, 메모리를 돌며 소켓에 주석 한 줄을 쓸 뿐이고 실제로 걷어낸 방이 있을 때만 DB 를 읽는다
    private static final long SWEEP_INTERVAL_MILLIS = 500L;

    // 마감 방송이 유실된 방의 연결이 이 시간 안에 끊긴다, 정상 경로는 방송 직후에 끊으므로 여기까지 오지 않는다
    // 위와 이유가 달라 값도 따로 간다, 이쪽은 구독이 있는 방마다 단계를 조회하므로 주기를 줄인 만큼 쿼리가 는다
    private static final long DISCONNECT_INTERVAL_MILLIS = 5_000L;

    private final AuctionRoomReader auctionRoomReader;
    private final RoomChannel roomChannel;

    /**
     * 구독을 등록하고 방 전체에 현황을 보낸다, 새 구독은 첫 현황을 받고 기존 구독은 늘어난 접속자 수를 받는다
     */
    public void subscribe(long auctionId, RoomSubscriber subscriber) {
        // 연결을 열어 두지 않는 단계의 구독이 채널에 남지 않도록 등록 전에 판정한다
        RoomQueryResult result = auctionRoomReader.find(auctionId)
                .orElseThrow(() -> new BusinessException(ROOM_NOT_FOUND));

        result.phase().streamRejection().ifPresent(errorCode -> {
            throw new BusinessException(errorCode);
        });

        roomChannel.subscribe(auctionId, subscriber);

        // 접속자 수는 메모리에서 오므로 등록한 뒤에 세면 DB 를 다시 읽지 않아도 된다
        broadcast(result);
    }

    /**
     * 구독을 방에서 빼고 남은 구독에 줄어든 접속자 수를 보낸다
     */
    public void unsubscribe(long auctionId, RoomSubscriber subscriber) {
        // 걷어내기와 방 끊기가 먼저 빼 간 뒤에도 이 콜백은 돌아온다, 그때 갱신하면 같은 방을 두 번 읽는다
        if (roomChannel.unsubscribe(auctionId, subscriber)) {
            refresh(auctionId);
        }
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
     * 연결을 열어 두지 않는 단계가 된 방의 구독을 서버가 끊는다
     */
    // 마감 방송이 이미 끊고 지나가므로 여기 걸리는 것은 그 방송이 유실된 방뿐이다
    @Scheduled(fixedDelay = DISCONNECT_INTERVAL_MILLIS, scheduler = SchedulingConfig.ROOM_STREAM)
    public void disconnectClosedRooms() {
        for (long auctionId : roomChannel.subscribedAuctions()) {
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
        if (roomChannel.countViewers(auctionId) == 0) {
            return;
        }

        // 구독이 열려 있는 사이에 경매글이 내려갔다면 보낼 현황이 없다
        auctionRoomReader.find(auctionId)
                .ifPresent(this::broadcast);
    }

    // 경매글이 내려가 단계를 알 수 없으면 끊을 근거도 없으므로 그대로 둔다
    private boolean connectionsAreOver(long auctionId) {
        return auctionRoomReader.findPhase(auctionId)
                .map(phase -> !phase.allowsConnection())
                .orElse(false);
    }

    private void broadcast(RoomQueryResult result) {
        long auctionId = result.detail().auctionId();

        roomChannel.broadcast(auctionId, RoomState.of(result, roomChannel.countViewers(auctionId)));

        // 마감됐다는 마지막 현황까지 받고 나서 끊는다, 이 순서라야 화면이 끊김을 결과로 읽는다
        if (!result.phase().allowsConnection()) {
            roomChannel.closeRoom(auctionId);
        }
    }
}
