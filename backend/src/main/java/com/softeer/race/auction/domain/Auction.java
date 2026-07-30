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
        @Index(name = "idx_auction_end_time", columnList = "current_end_time, id")
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
}
