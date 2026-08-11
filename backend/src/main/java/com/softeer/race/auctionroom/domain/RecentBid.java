package com.softeer.race.auctionroom.domain;

import com.softeer.race.common.domain.MaskedName;
import com.softeer.race.user.domain.Role;

import java.time.LocalDateTime;

/**
 * 호가창에 보이는 입찰 한 건
 */
public final class RecentBid {

    // 내 입찰인지 판단하기 위해서, 접근자를 만들지 않아 밖으로 나갈 길이 없다
    private final long bidderId;
    private final MaskedName bidderName;
    private final Role role;
    private final long amount;
    private final LocalDateTime bidAt;

    // JPQL 생성자 표현식이 인자 타입으로 이 생성자에 걸린다
    // 원본은 필드로 남지 않아 꺼낼 접근자가 없고, 마스킹을 빠뜨리는 실수가 타입으로 막힌다
    public RecentBid(long bidderId, String realName, Role role, long amount, LocalDateTime bidAt) {
        this.bidderId = bidderId;
        this.bidderName = MaskedName.mask(realName);
        this.role = role;
        this.amount = amount;
        this.bidAt = bidAt;
    }

    /**
     * 조회한 사람이 넣은 호가인지
     */
    public boolean isMine(long viewerId) {
        return bidderId == viewerId;
    }

    public MaskedName bidderName() {
        return bidderName;
    }

    public Role role() {
        return role;
    }

    public long amount() {
        return amount;
    }

    public LocalDateTime bidAt() {
        return bidAt;
    }
}