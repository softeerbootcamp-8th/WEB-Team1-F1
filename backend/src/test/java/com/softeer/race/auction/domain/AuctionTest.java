package com.softeer.race.auction.domain;

import com.softeer.race.auction.exception.AuctionErrorCode;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 27, 15, 30);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 7, 27, 16, 30);
    // schedule()이 계산하는 마감 = START_TIME + 20분
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 7, 27, 16, 50);

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

    // 기대값의 30초는 일부러 하드코딩한다
    // 상수를 실수로 바꾸면 테스트가 깨져야 하므로 SOFT_CLOSE_WINDOW를 노출해 참조하지 않는다
    @DisplayName("잔여 시간이 30초 이하인 입찰만 마감을 입찰 시각 + 30초로 다시 채운다")
    @ParameterizedTest(name = "잔여 {0}초 → 연장 {1}")
    @CsvSource({
            "31, false", // 임계값 밖, 1초 차이로 갈리는 지점을 고정한다
            "30, true",  // 경계 포함 — FR-3.3.1이 "이하"다
            "29, true",
            "1,  true"   // 마감 직전
    })
    void extendsOnlyInsideWindow(long remainingSeconds, boolean extended) {
        Auction auction = scheduled();
        LocalDateTime bidAt = END_TIME.minusSeconds(remainingSeconds);

        auction.extendIfClosingSoon(bidAt);

        assertThat(auction.getCurrentEndTime())
                .isEqualTo(extended ? bidAt.plusSeconds(30) : END_TIME);
        assertThat(auction.getExtensionCount()).isEqualTo(extended ? 1 : 0);
    }

    @Test
    @DisplayName("연장이 반복돼도 마감은 마지막 입찰 시각 기준으로만 정해진다")
    void doesNotAccumulateExtensions() {
        Auction auction = scheduled();
        LocalDateTime firstBid = END_TIME.minusSeconds(10);   // 잔여 10초
        LocalDateTime secondBid = firstBid.plusSeconds(25);   // 새 마감 기준 잔여 5초

        auction.extendIfClosingSoon(firstBid);
        auction.extendIfClosingSoon(secondBid);

        // 누적 가산이면 원래 마감 + 60초가 된다, 이 단정이 누적 여부를 가리는 유일한 검증이다
        assertThat(auction.getCurrentEndTime()).isEqualTo(secondBid.plusSeconds(30));
        assertThat(auction.getExtensionCount()).isEqualTo(2);
    }

    @DisplayName("마감 시각에 도달한 입찰은 연장을 만들지 못한다")
    @ParameterizedTest(name = "마감 {0}초 후 입찰")
    @ValueSource(longs = {0, 1}) // 0 = 마감 정각(진행이 끝난 첫 순간), 1 = 마감 이후
    void rejectsBidAtOrAfterDeadline(long secondsAfterEnd) {
        Auction auction = scheduled();

        assertThatThrownBy(() -> auction.extendIfClosingSoon(END_TIME.plusSeconds(secondsAfterEnd)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Auction scheduled() {
        return Auction.schedule(post(), 10_000_000L, START_TIME);
    }

    private AuctionPost post() {
        return AuctionPost.create(null, null, PUBLISHED_AT);
    }
}
