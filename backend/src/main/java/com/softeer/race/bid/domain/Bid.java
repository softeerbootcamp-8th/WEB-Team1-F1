package com.softeer.race.bid.domain;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false)
    private long amount;

    /**
     * 성립한 입찰 한 건
     * 금액 판정은 BidRule이, 단계와 자격 판정은 BidService가 마친 뒤에 부른다
     * 여기서 다시 검사하지 않는다, 같은 규칙이 두 곳에 있으면 어느 쪽이 진짜인지 모호해진다
     */
    public static Bid place(Auction auction, User bidder, long amount) {
        Bid bid = new Bid();
        bid.auction = auction;
        bid.bidder = bidder;
        bid.amount = amount;

        return bid;
    }
}
