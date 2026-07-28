package com.softeer.race.auctionroom.domain;

import java.time.LocalDateTime;

/**
 * 호가창에 보이는 입찰 한 건
 */
public record RecentBid(
        MaskedName bidderName,
        long amount,
        LocalDateTime bidAt
) {
    /**
     * 원본 이름을 마스킹해 담는 생성자
     */
    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    // 원본은 필드로 남지 않아 꺼낼 접근자가 없고, 마스킹을 빠뜨리는 실수가 타입으로 막힌다
    public RecentBid(String realName, long amount, LocalDateTime bidAt) {
        this(new MaskedName(realName), amount, bidAt);
    }
}