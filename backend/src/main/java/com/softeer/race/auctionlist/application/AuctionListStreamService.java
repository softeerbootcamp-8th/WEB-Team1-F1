package com.softeer.race.auctionlist.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 목록 변화를 열려 있는 구독으로 흘려보내는 서비스
 */
// @Transactional 을 붙이지 않는다, 방송이 소켓 쓰기라 안 받아 가는 구독자 하나에 커넥션이 묶인다
@Service
@RequiredArgsConstructor
public class AuctionListStreamService {

    private final AuctionListChannel auctionListChannel;

    /**
     * 구독을 명부에 올린다, 경매방과 달리 첫 현황을 보내지 않는다
     */
    // 서버가 보고 있는 페이지를 몰라 "첫 현황"이 정의되지 않는다, 복구는 화면의 재조회 몫이다
    public void subscribe(AuctionListSubscriber subscriber) {
        auctionListChannel.subscribe(subscriber);
    }

    /**
     * 구독을 명부에서 뺀다
     */
    public void unsubscribe(AuctionListSubscriber subscriber) {
        auctionListChannel.unsubscribe(subscriber);
    }
}