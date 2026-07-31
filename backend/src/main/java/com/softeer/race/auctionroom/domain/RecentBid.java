package com.softeer.race.auctionroom.domain;

import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 호가창에 보이는 입찰 한 건
 */
public record RecentBid(
        long bidderId,
        MaskedName bidderName,
        Role role,
        long amount,
        LocalDateTime bidAt
) {
    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    // 원본은 필드로 남지 않아 꺼낼 접근자가 없고, 마스킹을 빠뜨리는 실수가 타입으로 막힌다
    public RecentBid(long bidderId, String realName, Role role, long amount, LocalDateTime bidAt) {
        this(bidderId, new MaskedName(realName), role, amount, bidAt);
    }

    /**
     * 조회한 사람이 넣은 호가인지
     */
    public boolean isMine(long viewerId) {
        return bidderId == viewerId;
    }
}