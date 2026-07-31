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

    // 저장된 status가 아니라 서버 시각으로 판정한다, 경계는 phaseAt의 LIVE 구간과 같아야 한다
    // 시작 정각은 포함하고 마감 정각은 제외한다, 화면이 "진행 중"으로 보여준 경매만 입찰을 받는다
    @DisplayName("입찰은 시작 시각부터 마감 시각 직전까지만 받는다")
    @ParameterizedTest(name = "{0} → 입찰 가능 {1}")
    @CsvSource({
            "2026-07-27T16:00:00, false", // 방은 열렸지만 아직 대기 구간이다
            "2026-07-27T16:29:59, false",
            "2026-07-27T16:30:00, true",  // 시작 정각, 포함
            "2026-07-27T16:49:59, true",
            "2026-07-27T16:50:00, false"  // 마감 정각, 제외
    })
    void isBiddableOnlyBetweenStartAndEnd(LocalDateTime now, boolean biddable) {
        assertThat(scheduled().isBiddableAt(now)).isEqualTo(biddable);
    }

    @Test
    @DisplayName("입찰이 성립하면 현재가와 갱신 시각만 바뀐다")
    void acceptBidUpdatesPriceOnly() {
        Auction auction = scheduled();
        LocalDateTime bidAt = START_TIME.plusMinutes(5);

        auction.acceptBid(12_000_000L, bidAt);

        assertThat(auction.getCurrentPrice()).isEqualTo(12_000_000L);
        assertThat(auction.getPriceUpdatedAt()).isEqualTo(bidAt);
        // 낙찰 확정과 상태 전환은 마감 뒤의 일이라 최고가 갱신이 건드리지 않는다
        assertThat(auction.getWinner()).isNull();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
    }

    // 위 extendsOnlyInsideWindow와 겹쳐 보이지만 검증 대상이 다르다
    // 저건 연장 규칙 자체를, 이건 acceptBid가 그 규칙에 판정을 넘기는지를 본다
    // 위임을 빼먹으면 기존 연장 테스트는 전부 통과하고 이 케이스만 깨진다
    @DisplayName("입찰 성립은 마감 연장 판정까지 함께 처리한다")
    @ParameterizedTest(name = "잔여 {0}초 → 연장 {1}")
    @CsvSource({
            "31, false",
            "30, true"
    })
    void acceptBidDelegatesExtension(long remainingSeconds, boolean extended) {
        Auction auction = scheduled();
        LocalDateTime bidAt = END_TIME.minusSeconds(remainingSeconds);

        auction.acceptBid(12_000_000L, bidAt);

        assertThat(auction.getCurrentEndTime())
                .isEqualTo(extended ? bidAt.plusSeconds(30) : END_TIME);
        assertThat(auction.getExtensionCount()).isEqualTo(extended ? 1 : 0);
    }

    // acceptBid의 사전조건, isBiddableAt이 통과시킨 시각만 들어와야 한다
    // 두 판정이 다른 시각을 봤다는 뜻이라 사용자가 재시도할 수 있는 실패가 아니다
    @DisplayName("마감을 지난 시각으로 입찰을 반영하면 서버 결함으로 중단한다")
    @Test
    void acceptBidAfterDeadlineFails() {
        assertThatThrownBy(() -> scheduled().acceptBid(12_000_000L, END_TIME))
                .isInstanceOf(IllegalStateException.class);
    }

    private Auction scheduled() {
        return Auction.schedule(post(), 10_000_000L, START_TIME);
    }

    private AuctionPost post() {
        return AuctionPost.create(null, null, PUBLISHED_AT);
    }
}
