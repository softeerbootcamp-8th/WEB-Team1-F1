package com.softeer.race.auctionroom.application;

/**
 * 경매방 현황을 받아가는 열린 구독
 */
public interface RoomSubscription {

    /**
     * 한 건 전송, 이미 닫혔거나 같은 종류로 이미 보낸 것보다 낡았으면 조용히 버린다
     */
    // 낡은 것을 버리는 것이 계약인 이유는, 입찰과 입퇴장마다 다른 스레드가 각자 만들어 같은 구독에 쓰기 때문이다
    // 늦게 만든 것이 먼저 닿으면 화면의 현재가나 사람 수가 되돌아간다, 구현은 검사와 전송을 원자적으로 묶어야 한다
    void send(RoomMessage message);

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
