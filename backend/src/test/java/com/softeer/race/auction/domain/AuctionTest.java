package com.softeer.race.auction.domain;

import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 27, 15, 30);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 7, 27, 16, 30);

    @Test
    @DisplayName("경매를 예약하면 방 개설은 시작 30분 전, 마감은 시작 20분 후가 된다.")
    void schedule_시각_계산() {
        Auction auction = Auction.schedule(post(), 10_000_000L, START_TIME);

        assertThat(auction.getRoomOpenAt()).isEqualTo(LocalDateTime.of(2026, 7, 27, 16, 0));
        assertThat(auction.getStartTime()).isEqualTo(START_TIME);
        assertThat(auction.getCurrentEndTime()).isEqualTo(LocalDateTime.of(2026, 7, 27, 16, 50));
    }

    @Test
    @DisplayName("예약된 경매는 SCHEDULED 상태이며 입찰 이력이 없다.")
    void schedule_초기_상태() {
        Auction auction = Auction.schedule(post(), 10_000_000L, START_TIME);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        assertThat(auction.getExtensionCount()).isZero();
        assertThat(auction.getCurrentPrice()).isNull();
        assertThat(auction.getWinner()).isNull();
        assertThat(auction.getStartPrice()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("시작 시각이 발행 시각으로부터 정확히 1시간 뒤면 예약할 수 있다")
    void schedule_시작시각_정확히_1시간_통과() {
        Auction auction = Auction.schedule(post(), 25_000_000L, START_TIME);

        assertThat(auction.getStartTime()).isEqualTo(START_TIME);
    }

    @Test
    @DisplayName("시작 시각이 발행 시간보다 1시간 미만이면 예약할 수 없다.")
    void schedule_시작시각_1시간미만_거부() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 27, 16, 29);

        assertThatThrownBy(() -> Auction.schedule(post(), 25_000_000L, startTime))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.INVALID_START_AT);
    }

    private AuctionPost post() {
        return AuctionPost.create(null, null, PUBLISHED_AT);
    }
}
