package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;

/**
 * 목록 변화를 받아가는 열린 구독
 */
// 어느 메서드도 던지지 않는 것이 계약이다, 순회 중에 하나가 터지면 남은 구독이 그 방송을 못 받는다
public interface AuctionListSubscriber {

    /**
     * 카드 한 장 전송, 이미 닫혔으면 조용히 버린다
     */
    void sendCard(AuctionCardInfo card);

    /**
     * 경매 하나의 시청자 수 전송, 이미 닫혔으면 조용히 버린다
     */
    // 카드로 통일하지 않는다, 1초마다 보내는 값이라 카드로 싸면 활성 경매 수만큼 매 초 조회가 나간다
    void sendAudience(long auctionId, int connectedCount);

    /**
     * 살아 있는지 확인만 하는 신호, 목록 변화를 담지 않는다
     */
    void ping();

    /**
     * 서버가 연결을 끝낸다, 이미 끝났으면 아무 일도 없다
     */
    void close();

    /**
     * 아직 살아 있는지, 채널이 닫힌 구독을 걷어낼 때 묻는다
     */
    boolean isOpen();
}