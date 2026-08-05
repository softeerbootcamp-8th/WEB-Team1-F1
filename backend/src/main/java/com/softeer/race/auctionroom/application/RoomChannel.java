package com.softeer.race.auctionroom.application;

import java.util.Set;

/**
 * 경매방별로 열려 있는 구독을 모아 두고 현황을 흘려보내는 채널
 */
public interface RoomChannel {

    /**
     * 구독을 방에 등록한다, 같은 구독을 두 번 등록해도 하나다
     */
    void subscribe(long auctionId, RoomSubscriber subscriber);

    /**
     * 구독을 방에서 뺀다, 두 번 빼도 명부는 같고 실제로 뺀 호출만 참을 돌려준다
     */
    // 걷어내기가 먼저 빼 간 뒤에 해제 콜백이 돌아오므로, 호출자는 자기가 뺀 것인지 알아야
    // 갱신을 두 번 돌리지 않는다
    boolean unsubscribe(long auctionId, RoomSubscriber subscriber);

    /**
     * 방에 등록된 구독 수, 접속자로 셀지는 단계가 정한다
     */
    int countSubscribers(long auctionId);

    /**
     * 방의 모든 구독에 현황 전송, 닫힌 구독은 순회가 끝난 뒤 걷어낸다
     */
    void broadcast(long auctionId, RoomState state);

    /**
     * 모든 방을 찔러 보고 닫힌 구독을 걷어낸다, 실제로 걷어낸 방의 식별자를 돌려준다
     */
    Set<Long> sweepClosed();

    /**
     * 구독이 등록된 방의 식별자, 아직 걷히지 않은 죽은 구독도 방을 목록에 남긴다
     */
    Set<Long> subscribedAuctions();

    /**
     * 방의 모든 구독을 서버가 끝낸다, 명부에서 먼저 빼므로 끝내는 동안 갱신이 돌지 않는다
     */
    void closeRoom(long auctionId);
}
