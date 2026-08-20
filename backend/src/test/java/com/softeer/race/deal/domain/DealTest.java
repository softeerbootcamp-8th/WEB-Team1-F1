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

    private static final LocalDateTime TRANSPORT_AT = NOW.plusDays(5);
    private static final LocalDateTime DELIVERY_AT = TRANSPORT_AT.plusDays(1);

    private static final String DOCUMENT_URL = "https://cdn.race.dev/deal/doc.pdf";
    private static final String TRANSPORT_LOCATION = "서울시 강남구 테헤란로 123";
    private static final String DELIVERY_LOCATION = "부산시 해운대구 센텀중앙로 55";

    private static final long START_PRICE = 20_000_000L;
    private static final long FINAL_PRICE = 30_000_000L;

    private static final User SELLER = User.create(
            "seller", "seller@race.dev", "encoded", "박판매", "01011112222", Role.GENERAL);
    private static final User BUYER = User.create(
            "buyer", "buyer@race.dev", "encoded", "김구매", "01033334444", Role.DEALER);

    @Test
    @DisplayName("낙찰로 연 거래는 구매 확정 대기에서 시작하고 양쪽 당사자와 낙찰가를 담는다")
    void start_초기_상태() {
        Deal deal = Deal.start(auction(), SELLER, BUYER, FINAL_PRICE, NOW);

        assertThat(deal.getStatus()).isEqualTo(DealStatus.BUYER_CONFIRM_PENDING);
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
    @DisplayName("낙찰부터 확정까지 세 번의 행동으로 끝나고 값이 모두 남는다")
    void 전체_흐름() {
        Deal deal = deal();

        deal.confirmPurchase(NOW.plusMinutes(10));
        assertThat(deal.getStatus()).isEqualTo(DealStatus.SELLER_SUBMIT_PENDING);

        deal.submitTransport(DOCUMENT_URL, TRANSPORT_AT, TRANSPORT_LOCATION, NOW.plusHours(1));
        assertThat(deal.getStatus()).isEqualTo(DealStatus.BUYER_SCHEDULE_PENDING);

        deal.confirmDelivery(DELIVERY_AT, DELIVERY_LOCATION, NOW.plusHours(2));

        assertThat(deal.getStatus()).isEqualTo(DealStatus.CONFIRMED);
        assertThat(deal.getDocumentUrl()).isEqualTo(DOCUMENT_URL);
        assertThat(deal.getTransportAt()).isEqualTo(TRANSPORT_AT);
        assertThat(deal.getTransportLocation()).isEqualTo(TRANSPORT_LOCATION);
        assertThat(deal.getDeliveryAt()).isEqualTo(DELIVERY_AT);
        assertThat(deal.getDeliveryLocation()).isEqualTo(DELIVERY_LOCATION);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    @DisplayName("단계를 건너뛰려는 요청은 거부된다")
    void 건너뛰기() {
        // 구매 확정 없이 판매자가 먼저 서류를 내려는 경우다
        assertThatThrownBy(() ->
                deal().submitTransport(DOCUMENT_URL, TRANSPORT_AT, TRANSPORT_LOCATION, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());
    }

    @Test
    @DisplayName("같은 확정 요청이 두 번 와도 두 번째는 거부되고 기록이 밀리지 않는다")
    void 중복_요청() {
        // 중복 클릭 방어의 1차 장치다. 진짜로 겹쳐 들어오는 경우는 @Version 이 커밋에서 막는다
        Deal deal = deal();
        deal.confirmPurchase(NOW.plusHours(1));

        assertThatThrownBy(() -> deal.confirmPurchase(NOW.plusHours(2)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());

        // 변경 시각까지 그대로여야 한다, 밀리면 기한이 재요청만으로 연장된다
        assertThat(deal.getStatus()).isEqualTo(DealStatus.SELLER_SUBMIT_PENDING);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    @DisplayName("정상 전이로는 취소 상태에 도달할 수 없다")
    void 전이로_취소_불가() {
        // 여기가 뚫리면 사유 없는 취소가 성립하고, 귀책을 정할 근거가 사라진다
        assertThatThrownBy(() -> deal().transitionTo(DealStatus.CANCELLED, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());
    }

    @Test
    @DisplayName("탁송 일시가 현재보다 앞서면 제출이 거부된다")
    void 탁송_일시_과거() {
        Deal deal = deal();
        deal.confirmPurchase(NOW);

        assertThatThrownBy(() ->
                deal.submitTransport(DOCUMENT_URL, NOW.minusMinutes(1), TRANSPORT_LOCATION, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.PAST_TRANSPORT_SCHEDULE.message());
    }

    @Test
    @DisplayName("인수 일시가 탁송 출발 일시보다 앞서면 확정이 거부된다")
    void 인도가_탁송보다_앞섬() {
        // 현재보다는 미래여도 탁송보다 앞설 수 있다, 그러면 차가 출발하기 전에 받는 약속이 된다
        Deal deal = 서류까지_낸_거래();

        assertThatThrownBy(() ->
                deal.confirmDelivery(TRANSPORT_AT.minusHours(1), DELIVERY_LOCATION, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.DELIVERY_BEFORE_TRANSPORT.message());
    }

    @Test
    @DisplayName("인수 일시가 탁송 출발 일시와 같아도 확정이 거부된다")
    void 인도가_탁송과_동시() {
        Deal deal = 서류까지_낸_거래();

        assertThatThrownBy(() -> deal.confirmDelivery(TRANSPORT_AT, DELIVERY_LOCATION, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.DELIVERY_BEFORE_TRANSPORT.message());
    }

    @Test
    @DisplayName("남의 차례에 보낸 요청은 값 오류가 아니라 단계 오류로 답한다")
    void 단계_검사가_값_검증보다_먼저다() {
        // 순서가 뒤집히면 아직 구매 확정도 안 된 거래에 "날짜가 과거"라고 답해서,
        // 무엇이 틀렸는지 알 수 없는 응답이 나간다
        assertThatThrownBy(() ->
                deal().submitTransport(DOCUMENT_URL, NOW.minusDays(1), TRANSPORT_LOCATION, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.INVALID_TRANSITION.message());
    }

    @Test
    @DisplayName("취소하면 사유가 남고 사유가 귀책을 알려 준다")
    void cancel_정상() {
        Deal deal = deal();

        deal.cancel(CancellationReason.BUYER_CANCELLED, NOW.plusDays(1));

        assertThat(deal.getStatus()).isEqualTo(DealStatus.CANCELLED);
        assertThat(deal.getCancellationReason()).isEqualTo(CancellationReason.BUYER_CANCELLED);
        assertThat(deal.getCancellationReason().faultParty()).isEqualTo(FaultParty.BUYER);
        assertThat(deal.getStatusChangedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("약속이 확정된 뒤에는 취소할 수 없다")
    void cancel_확정_이후() {
        Deal deal = 서류까지_낸_거래();
        deal.confirmDelivery(DELIVERY_AT, DELIVERY_LOCATION, NOW);

        assertThatThrownBy(() -> deal.cancel(CancellationReason.SELLER_CANCELLED, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DealErrorCode.NOT_CANCELLABLE.message());

        assertThat(deal.getStatus()).isEqualTo(DealStatus.CONFIRMED);
    }

    private Deal deal() {
        return Deal.start(auction(), SELLER, BUYER, FINAL_PRICE, NOW);
    }

    private Deal 서류까지_낸_거래() {
        Deal deal = deal();
        deal.confirmPurchase(NOW);
        deal.submitTransport(DOCUMENT_URL, TRANSPORT_AT, TRANSPORT_LOCATION, NOW);

        return deal;
    }

    // 거래는 경매를 참조만 하고 그 값을 읽지 않는다, 차량 없는 경매글로 충분하다
    private Auction auction() {
        return Auction.schedule(AuctionPost.create(null, PUBLISHED_AT), START_PRICE, START_TIME);
    }
}
