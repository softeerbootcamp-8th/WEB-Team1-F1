package com.softeer.race.deal.domain;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.deal.exception.DealErrorCode;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 거래가 스스로 지키는 규칙
 */
@DisplayName("거래 엔티티")
class DealTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 8, 8, 10, 0);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 8, 8, 11, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    private static final long START_PRICE = 20_000_000L;
    private static final long FINAL_PRICE = 30_000_000L;

    private static final User SELLER = User.create(
            "seller", "seller@race.dev", "encoded", "박판매", "01011112222", Role.GENERAL);
    private static final User BUYER = User.create(
            "buyer", "buyer@race.dev", "encoded", "김구매", "01033334444", Role.DEALER);

    @Test
    @DisplayName("낙찰로 연 거래는 보증금 단계에서 시작하고 양쪽 당사자와 낙찰가를 담는다")
    void start_초기_상태() {
        Deal deal = Deal.start(auction(), SELLER, BUYER, FINAL_PRICE, NOW);

        assertThat(deal.getStatus()).isEqualTo(DealStatus.DEPOSIT_PENDING);
        assertThat(deal.getSeller()).isEqualTo(SELLER);
        assertThat(deal.getBuyer()).isEqualTo(BUYER);
        assertThat(deal.getFinalPrice()).isEqualTo(FINAL_PRICE);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW);
        assertThat(deal.getCancellationReason()).isNull();
    }

    @Test
    @DisplayName("낙찰가가 비어 있으면 거래를 만들 수 없다")
    void start_낙찰가_없음() {
        // 낙찰자가 있는데 금액이 없는 것은 사용자가 고칠 수 있는 문제가 아니라 데이터가 깨진 것이다
        assertThatThrownBy(() -> Deal.start(auction(), SELLER, BUYER, null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "낙찰가 {0}")
    @ValueSource(longs = {0L, -1L})
    @DisplayName("낙찰가가 0 이하면 거래를 만들 수 없다")
    void start_낙찰가_비정상(long finalPrice) {
        assertThatThrownBy(() -> Deal.start(auction(), SELLER, BUYER, finalPrice, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("다음 단계로 넘기면 단계와 변경 시각이 함께 바뀐다")
    void transitionTo_정상() {
        Deal deal = deal();

        deal.transitionTo(DealStatus.DOCUMENT_PENDING, NOW.plusHours(1));

        assertThat(deal.getStatus()).isEqualTo(DealStatus.DOCUMENT_PENDING);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    @DisplayName("단계를 건너뛰려는 요청은 거부된다")
    void transitionTo_건너뛰기() {
        assertThatThrownBy(() -> deal().transitionTo(DealStatus.BALANCE_PENDING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());
    }

    @Test
    @DisplayName("같은 전이 요청이 두 번 와도 두 번째는 거부되고 기록이 밀리지 않는다")
    void transitionTo_재요청() {
        Deal deal = deal();
        deal.transitionTo(DealStatus.DOCUMENT_PENDING, NOW.plusHours(1));

        assertThatThrownBy(() -> deal.transitionTo(DealStatus.DOCUMENT_PENDING, NOW.plusHours(2)))
                .isInstanceOf(BusinessException.class);

        // 변경 시각까지 그대로여야 한다, 밀리면 기한이 재요청만으로 연장된다
        assertThat(deal.getStatus()).isEqualTo(DealStatus.DOCUMENT_PENDING);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    @DisplayName("정상 전이로는 취소 상태에 도달할 수 없다")
    void transitionTo_취소_불가() {
        // 여기가 뚫리면 사유 없는 취소가 성립하고, 보증금 향방을 정할 근거가 사라진다
        assertThatThrownBy(() -> deal().transitionTo(DealStatus.CANCELLED, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());
    }

    @Test
    @DisplayName("취소하면 사유가 남고 사유가 귀책을 알려 준다")
    void cancel_정상() {
        Deal deal = deal();

        deal.cancel(CancellationReason.DEPOSIT_TIMEOUT, NOW.plusDays(1));

        assertThat(deal.getStatus()).isEqualTo(DealStatus.CANCELLED);
        assertThat(deal.getCancellationReason()).isEqualTo(CancellationReason.DEPOSIT_TIMEOUT);
        assertThat(deal.getCancellationReason().faultParty()).isEqualTo(FaultParty.BUYER);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("잔금을 받은 뒤에는 취소할 수 없다")
    void cancel_잔금_이후() {
        Deal deal = deal();
        deal.transitionTo(DealStatus.DOCUMENT_PENDING, NOW);
        deal.transitionTo(DealStatus.TRANSPORT_PENDING, NOW);
        deal.transitionTo(DealStatus.BALANCE_PENDING, NOW);
        deal.transitionTo(DealStatus.HANDOVER_PENDING, NOW);

        assertThatThrownBy(() -> deal.cancel(CancellationReason.BALANCE_TIMEOUT, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.NOT_CANCELLABLE.message());

        assertThat(deal.getStatus()).isEqualTo(DealStatus.HANDOVER_PENDING);
    }

    private Deal deal() {
        return Deal.start(auction(), SELLER, BUYER, FINAL_PRICE, NOW);
    }

    // 거래는 경매를 참조만 하고 그 값을 읽지 않는다, 차량 없는 경매글로 충분하다
    private Auction auction() {
        return Auction.schedule(AuctionPost.create(null, PUBLISHED_AT), START_PRICE, START_TIME);
    }
}
