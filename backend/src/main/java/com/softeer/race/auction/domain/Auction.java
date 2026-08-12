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

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
// 목록 조회가 시작·마감 시각으로 범위를 좁히고 같은 컬럼으로 정렬한다.
// 인덱스가 없으면 매번 전체를 훑고 정렬하므로 종료된 경매가 쌓일수록 첫 페이지까지 느려진다.
@Table(indexes = {
        @Index(name = "idx_auction_start_time", columnList = "start_time, id"),
        @Index(name = "idx_auction_end_time", columnList = "current_end_time, id"),
        @Index(name = "idx_auction_status_start_time", columnList = "status, start_time"),
        @Index(name = "idx_auction_status_end_time", columnList = "status, current_end_time")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseTimeEntity {

    private static final int ROOM_OPEN_BEFORE_MINUTES = 30;
    private static final int DURATION_MINUTES = 20;
    private static final int MIN_LEAD_TIME_HOURS = 1;

    // 마감까지 남은 시간이 이 값 이하인 입찰만 마감을 밀어낸다
    // 재설정 폭도 같은 값이라 상수 하나로 둔다, 둘이 갈라지는 요구가 생기면 쪼갠다
    private static final Duration SOFT_CLOSE_WINDOW = Duration.ofSeconds(30);

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_bidder_id")
    private User topBidder;

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

    /**
     * 그 입찰이 마감을 밀어냈는지, 끝난 경매를 되짚을 때 쓴다
     */
    public static boolean isDeadlineExtending(LocalDateTime startTime, LocalDateTime bidAt) {
        return !bidAt.isBefore(startTime.plusMinutes(DURATION_MINUTES).minus(SOFT_CLOSE_WINDOW));
    }

    /**
     * 마감 임박 입찰이면 마감을 입찰 시각 기준으로 다시 채우고 연장 횟수를 올린다
     */
    public void extendIfClosingSoon(LocalDateTime bidAt) {
        // 입찰 가능 판정(phaseAt)을 통과한 입찰만 여기 온다
        // 마감된 경매가 도달했다면 판정과 연장이 서로 다른 시각을 봤다는 뜻이므로,
        // 호출자는 두 곳에 같은 기준 시각을 넘겨야 한다
        // 사용자가 고쳐 재시도할 수 있는 실패가 아니라 서버 결함이라 BusinessException을 쓰지 않는다
        if (!bidAt.isBefore(currentEndTime)) {
            throw new IllegalStateException(
                    "마감된 경매에 연장 판정이 들어왔다, 경매 %d 마감 %s 입찰 %s"
                            .formatted(id, currentEndTime, bidAt));
        }

        // 임계값에 들어오기 전 입찰은 마감 시각에 영향을 주지 않는다
        if (bidAt.isBefore(currentEndTime.minus(SOFT_CLOSE_WINDOW))) {
            return;
        }

        // 누적 가산이 아니라 재설정이다, 잔여 10초에 들어온 입찰도 40초가 아니라 30초를 받는다
        currentEndTime = bidAt.plus(SOFT_CLOSE_WINDOW);
        extensionCount++;
        // 연장 횟수에 상한을 두지 않는다
    }

    // 스케줄러가 들어오면 status와 시각 판정의 관계를 다시 정리한다.
    public boolean isBiddableAt(LocalDateTime now) {
        return !now.isBefore(startTime) && now.isBefore(currentEndTime);
    }

    public boolean isTopBidder(long bidderId) {
        return topBidder != null && topBidder.getId() == bidderId;
    }

    public void acceptBid(User bidder, long amount, LocalDateTime acceptedAt) {
        this.topBidder = bidder;
        this.currentPrice = amount;
        this.priceUpdatedAt = acceptedAt;
        extendIfClosingSoon(acceptedAt);
    }

    /**
     * 진행 중이고 마감에 도달한 경매인지
     * <p>
     * 예약 상태는 종료 대상이 아니다. 시작 전이를 건너뛰고 바로 낙찰로 뛰면
     * 예약 → 진행 중 → 종료 순서를 후보 조회 한 줄만 지키게 된다.
     * 이미 끝난 경매도 IN_PROGRESS 가 아니라 그대로 걸러지므로 재종료가 막힌다.
     * <p>
     * 종료 후보를 뽑은 시각과 잠금을 얻은 시각 사이에 소프트 클로즈로 마감이 밀릴 수 있다.
     * 호출자는 잠금을 얻은 뒤 이 판정을 다시 통과시켜야 한다.
     * <p>
     * isBiddableAt과 경계가 맞물린다. 마감 정각은 입찰이 안 되고 종료는 된다.
     */
    public boolean isClosableAt(LocalDateTime now) {
        return status == AuctionStatus.IN_PROGRESS && !now.isBefore(currentEndTime);
    }

    /**
     * 경매를 종료하고 낙찰자를 확정한다.
     * <p>
     * 현재가는 건드리지 않는다. 마지막으로 성립한 입찰이 이미 채워둔 값이 그대로 낙찰가다.
     *
     * @param now isClosableAt 판정에 쓴 것과 같은 값이어야 한다.
     *            호출자가 잠금을 얻은 뒤에 찍어야 한다.
     */
    public void close(LocalDateTime now) {
        // 호출자가 잠금 안에서 isClosableAt으로 걸렀다면 여기 도달할 수 없다.
        // 도달했다면 판정과 확정이 다른 시각을 봤거나 잠금 없이 불렀다는 뜻이라 서버 결함이다.
        // extendIfClosingSoon과 같은 이유로 BusinessException을 쓰지 않는다.
        if (!isClosableAt(now)) {
            throw new IllegalStateException(
                    "종료할 수 없는 경매에 확정이 들어왔다, 경매 %d 상태 %s 마감 %s 기준 %s".formatted(id, status, currentEndTime, now));
        }

        winner = topBidder;
        status = topBidder == null ? AuctionStatus.FAILED : AuctionStatus.ENDED;
    }

    /**
     * 시작 시각이 지났고 아직 예약 상태인지
     */
    public boolean isStartableAt(LocalDateTime now) {
        return status == AuctionStatus.SCHEDULED && !now.isBefore(startTime);
    }

    public void start(LocalDateTime now) {
        if (!isStartableAt(now)) {
            throw new IllegalStateException("시작할 수 없는 경매에 전이가 들어왔다, 경매 %d 상태 %s 시작 %s 기준 %s"
                    .formatted(id, status, startTime, now));
        }

        status = AuctionStatus.IN_PROGRESS;
    }

    public boolean isEditableAt(LocalDateTime now) {
        return now.isBefore(roomOpenAt);
    }

    public void updateBeforeRoomOpens(long startPrice, LocalDateTime startTime, LocalDateTime now) {
        if (!isEditableAt(now)) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ROOM_ALREADY_OPEN);
        }
        // 리드타임 기준은 발행 시각이 아니라 지금이다. publishedAt은 과거에 고정된 값이라
        // 그대로 쓰면 발행 후 시간이 오래 지난 뒤에는 리드타임 없이 임박한 시각으로도 바꿀 수 있게 된다.
        validateStartTime(startTime, now);

        this.startPrice = startPrice;
        this.roomOpenAt = startTime.minusMinutes(ROOM_OPEN_BEFORE_MINUTES);
        this.startTime = startTime;
        this.currentEndTime = startTime.plusMinutes(DURATION_MINUTES);
    }

}
