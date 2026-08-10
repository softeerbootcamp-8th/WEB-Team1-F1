package com.softeer.race.auctionlist.application;

/**
 * 목록 변화를 받아가는 열린 구독
 */
public interface AuctionListSubscriber {

    /**
     * 서버가 연결을 끝낸다, 이미 끝났으면 아무 일도 없고 던지지 않는다
     */
    // 던지지 않는 것이 계약이다, 그래야 채널이 방어 없이 걷어낼 수 있다
    void close();

    /**
     * 아직 살아 있는지, 채널이 닫힌 구독을 걷어낼 때 묻는다
     */
    boolean isOpen();
}