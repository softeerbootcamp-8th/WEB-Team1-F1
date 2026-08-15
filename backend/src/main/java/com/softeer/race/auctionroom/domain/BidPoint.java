package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.Auction;

import java.time.LocalDateTime;

/**
 * 가격 곡선에 찍히는 입찰 한 건
 */
public final class BidPoint {

    // 내 입찰인지 판단하기 위해서, 접근자를 만들지 않아 밖으로 나갈 길이 없다
    private final long bidderId;
    private final long amount;
    private final LocalDateTime bidAt;

    // JPQL 생성자 표현식이 이 생성자에 걸린다
    // 이름을 받지 않는 것이 호가창과 다른 점이다, 곡선은 누가 불렀는지를 그리지 않는다
    public BidPoint(long bidderId, long amount, LocalDateTime bidAt) {
        this.bidderId = bidderId;
        this.amount = amount;
        this.bidAt = bidAt;
    }

    /**
     * 조회한 사람이 찍은 점인지
     */
    public boolean isMine(long viewerId) {
        return bidderId == viewerId;
    }

    /**
     * 이 입찰이 마감을 밀어냈는지, 판정은 마감 규칙을 가진 경매에 물어본다
     */
    public boolean extendsDeadline(LocalDateTime startTime) {
        return Auction.isDeadlineExtending(startTime, bidAt);
    }

    public long amount() {
        return amount;
    }

    public LocalDateTime bidAt() {
        return bidAt;
    }
}
