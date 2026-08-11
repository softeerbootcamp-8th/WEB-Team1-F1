package com.softeer.race.auctionroom.domain;

import java.time.LocalDateTime;

/**
 * 가격이 오른 과정을 그리는 점 하나
 */
public final class PricePoint {

    // 내 입찰인지 판단하기 위해서, 접근자를 만들지 않아 밖으로 나갈 길이 없다
    private final long bidderId;
    private final long amount;
    private final LocalDateTime bidAt;

    // JPQL 생성자 표현식이 이 생성자에 걸린다
    // 이름을 받지 않는 것이 호가창과 다른 점이다, 곡선은 누가 불렀는지를 그리지 않는다
    public PricePoint(long bidderId, long amount, LocalDateTime bidAt) {
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

    public long amount() {
        return amount;
    }

    public LocalDateTime bidAt() {
        return bidAt;
    }
}
