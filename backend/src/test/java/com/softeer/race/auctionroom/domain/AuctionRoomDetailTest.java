package com.softeer.race.auctionroom.domain;

import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 결과 응답은 시각이 아니라 확정된 상태를 본다
// 마감 정각과 스케줄러가 낙찰자를 확정하는 순간 사이에는 낙찰인지 유찰인지 알 수 없다
class AuctionRoomDetailTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 8, 3, 20, 30);

    @Test
    @DisplayName("낙찰된 경매는 낙찰 결과와 최종 낙찰가, 마스킹된 낙찰자를 내놓는다")
    void soldAuctionHasWinnerAndPrice() {
        AuctionRoomDetail detail = sold(21_000_000L);

        assertThat(detail.outcome()).contains(AuctionOutcome.SOLD);
        assertThat(detail.winningPrice()).contains(21_000_000L);
        assertThat(detail.winnerName()).map(MaskedName::value).contains("이*호");
        assertThat(detail.isWonBy(WINNER_ID)).isTrue();
    }

    @Test
    @DisplayName("유찰된 경매는 결과가 유찰이고 낙찰가도 낙찰자도 없다")
    void unsoldAuctionHasNeitherPriceNorWinner() {
        AuctionRoomDetail detail = unsold();

        assertThat(detail.outcome()).contains(AuctionOutcome.UNSOLD);
        assertThat(detail.winningPrice()).isEmpty();
        assertThat(detail.winnerName()).isEmpty();
    }

    @Test
    @DisplayName("아직 확정되지 않은 경매는 결과 자체가 없다")
    void unsettledAuctionHasNoOutcome() {
        assertThat(detail(AuctionStatus.SCHEDULED, null, null, null).outcome()).isEmpty();
        assertThat(detail(AuctionStatus.IN_PROGRESS, 11_000_000L, null, null).outcome()).isEmpty();
    }

    @Test
    @DisplayName("진행 중에 붙은 현재가는 낙찰가로 새어 나가지 않는다")
    void currentPriceIsNotAWinningPriceUntilSettled() {
        assertThat(detail(AuctionStatus.IN_PROGRESS, 11_000_000L, null, null).winningPrice()).isEmpty();
    }

    @Test
    @DisplayName("입찰이 없으면 현재가는 시작가다")
    void currentPriceFallsBackToStartPrice() {
        assertThat(detail(AuctionStatus.IN_PROGRESS, null, null, null).currentPrice()).isEqualTo(START_PRICE);
    }

    @Test
    @DisplayName("입찰이 있으면 현재가를 그대로 쓴다")
    void currentPriceUsesLatestBid() {
        assertThat(detail(AuctionStatus.IN_PROGRESS, 11_000_000L, null, null).currentPrice())
                .isEqualTo(11_000_000L);
    }

    // ================= 픽스처 ====================

    private static final long WINNER_ID = 7L;
    private static final long START_PRICE = 10_000_000L;

    private static AuctionRoomDetail sold(long winningPrice) {
        return detail(AuctionStatus.ENDED, winningPrice, WINNER_ID, "이준호");
    }

    private static AuctionRoomDetail unsold() {
        return detail(AuctionStatus.FAILED, null, null, null);
    }

    private static AuctionRoomDetail detail(
            AuctionStatus status, Long currentPrice, Long winnerId, String winnerRealName) {

        return new AuctionRoomDetail(
                1L,
                status,
                START_PRICE,
                currentPrice,
                START_AT.minusMinutes(30),
                START_AT,
                START_AT.plusMinutes(20),
                Manufacturer.HYUNDAI,
                "아반떼 CN7",
                2022,
                35_000,
                FuelType.GASOLINE,
                "https://cdn.race.dev/avante.jpg",
                "https://cdn.race.dev/avante-report.pdf",
                winnerId,
                winnerRealName);
    }
}
