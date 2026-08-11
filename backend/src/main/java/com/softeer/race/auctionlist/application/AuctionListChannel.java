package com.softeer.race.auctionlist.application;

import com.softeer.race.auctionlist.application.dto.AuctionCardInfo;

/**
 * 목록을 보고 있는 구독을 모아 두고 변화를 흘려보내는 채널
 */
// 명부가 경매별이 아니라 하나다, 목록 구독자는 특정 경매가 아니라 목록 전체를 본다
public interface AuctionListChannel {

    /**
     * 구독을 명부에 올린다, 같은 구독을 두 번 올려도 하나다
     */
    void subscribe(AuctionListSubscriber subscriber);

    /**
     * 구독을 명부에서 뺀다, 없던 것을 빼도 아무 일 없다
     */
    void unsubscribe(AuctionListSubscriber subscriber);

    /**
     * 열려 있는 모든 구독에 카드 전송, 닫힌 구독은 순회가 끝난 뒤 걷어낸다
     */
    void broadcastCard(AuctionCardInfo card);

    /**
     * 열려 있는 모든 구독에 시청자 수 전송, 닫힌 구독은 순회가 끝난 뒤 걷어낸다
     */
    void broadcastAudience(long auctionId, int connectedCount);

    /**
     * 모든 구독을 찔러 보고 닫힌 것을 걷어낸다
     */
    // 경매방과 달리 걷어낸 대상을 돌려주지 않는다, 걷어낸 뒤 보낼 것이 없다
    void sweepClosed();

    /**
     * 목록을 보고 있는 구독이 하나라도 있는지
     */
    // 방송이 이것으로 먼저 걸러 아무도 안 볼 때 DB 를 읽지 않는다
    boolean hasSubscribers();
}