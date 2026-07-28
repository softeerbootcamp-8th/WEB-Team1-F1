package com.softeer.race.auction.domain;

import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseTimeEntity {

    private static final int ROOM_OPEN_BEFORE_MINUTES = 30;
    private static final int DURATION_MINUTES = 20;
    private static final int MIN_LEAD_TIME_HOURS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private AuctionPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false)
    private long startPrice;

    private Long currentPrice;

    @Column(nullable = false)
    private LocalDateTime roomOpenAt;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime currentEndTime;

    @Column(nullable = false)
    private int extensionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    private LocalDateTime priceUpdatedAt;

    /**
     * 시작 시각으로부터 방 개설 시각과 마감 시각을 계산해 예약 상태의 경매를 만든다
     */
    public static Auction schedule(AuctionPost post, long startPrice, LocalDateTime startTime) {
        validateStartTime(startTime, post.getPublishedAt());

        Auction auction = new Auction();
        auction.post = post;
        auction.startPrice = startPrice;
        auction.roomOpenAt = startTime.minusMinutes(ROOM_OPEN_BEFORE_MINUTES);
        auction.startTime = startTime;
        auction.currentEndTime = startTime.plusMinutes(DURATION_MINUTES);
        auction.extensionCount = 0;
        auction.status = AuctionStatus.SCHEDULED;

        return auction;
    }

    // 발행 직후 시작하는 경매를 막기 위해, 발행 시각으로부터 최소 리드타임을 요구한다
    private static void validateStartTime(LocalDateTime startTime, LocalDateTime publishTime) {
        if (startTime.isBefore(publishTime.plusHours(MIN_LEAD_TIME_HOURS))) {
            throw new BusinessException(AuctionErrorCode.INVALID_START_AT);
        }
    }
}
