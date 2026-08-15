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
     * 서버가 연결을 끝낸다, 이미 끝났으면 아무 일도 없고 던지지 않는다
     */
    // 던지지 않는 것이 계약이다, 그래야 채널이 방어 없이 방 하나를 통째로 끊을 수 있다
    void close();

    /**
     * 아직 살아 있는지, 채널이 닫힌 구독을 걷어낼 때 묻는다
     */
    boolean isOpen();

    /**
     * 이 구독을 연 사람, 같은 사람이 연 창들을 하나로 묶는 데만 쓴다
     */
    // 방송에 싣지 않는다, 그것을 지키는 것은 이 주석이 아니라 RoomStreamIntegrationTest 의 누출 단정이다
    long viewerId();
}
