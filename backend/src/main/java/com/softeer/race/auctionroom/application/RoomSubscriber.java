package com.softeer.race.auctionroom.application;

/**
 * 경매방 현황을 받아가는 열린 구독
 */
public interface RoomSubscriber {

    /**
     * 현황 전송, 이미 닫혔으면 조용히 버린다
     */
    void send(RoomState state);

    /**
     * 살아 있는지 확인만 하는 신호, 현황을 담지 않는다
     */
    void ping();

    /**
     * 아직 살아 있는지, 채널이 닫힌 구독을 걷어낼 때 묻는다
     */
    boolean isOpen();
}
